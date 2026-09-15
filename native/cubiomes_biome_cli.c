/*
 * Thin cubiomes CLI for JavaSeedSearch3.
 *
 * Protocol (stdin lines):
 *   BIOME <seed> <dim> <blockX> <blockY> <blockZ>
 *     → OK <id> <name>
 *   SEEDS <n> <dim> <blockX> <blockY> <blockZ>
 *     n lines: <seed>
 *     → n lines: <id>
 *   SEEDSXZ <n> <dim> <blockY>
 *     n lines: <seed> <chunkX> <chunkZ>   (samples at chunk origin block XZ, fixed Y)
 *     → n lines: <id>
 *   BATCH <n>
 *     n lines: <seed> <dim> <blockX> <blockY> <blockZ>
 *     → n lines: OK <id> <name>
 *   VILLAGE <seed>
 *     region (0,0) village: getStructurePos + isViableStructurePos
 *     → OK <blockX> <blockZ> <biomeId>   or FAIL
 *   VILLAGEPOS <seed>
 *     region (0,0) getStructurePos only (48-bit; same for sister seeds)
 *     → OK <blockX> <blockZ>   or FAIL
 *   FSGCHECK <n> <portalBX> <portalBY> <portalBZ> <vilX> <vilZ>
 *     n lines: <seed>
 *     One applySeed per seed: village isViable first (tighter); on success sample
 *     portal biome at fixed XZ/Y.
 *     → n lines: <villageOk 0|1> <portalBiomeId>   (biomeId=0 when village fails)
 *   SPAWN <seed>
 *     cubiomes getSpawn (1.18+ fittest + spiral) overworld
 *     → OK <blockX> <blockZ>
 *   STRUCTFSG <seed>
 *     48-bit FSG structure prefilter (portal/village chunk range + nether
 *     bastion≤100 + fortress≤160 of bastion). Nether uses applySeed(DIM_NETHER):
 *     bastion 3/5 roll + isViable (basalt deltas → fortress); fortress where
 *     bastion does not generate.
 *     → OK <pcx> <pcz> <vcx> <vcz> <bcx> <bcz> <fcx> <fcz>   (chunk coords)
 *     → FAIL
 *   STRUCTFSGBATCH <n>
 *     n lines: <seed>
 *     → n lines: 0   or   1 <pcx> <pcz> <vcx> <vcz> <bcx> <bcz> <fcx> <fcz>
 *   QUIT
 *
 * Samples at cubiomes scale 4 (= vanilla QuartPos / structure biome check).
 * Uses MC_1_21 (MC_1_21_WD).
 */
#include "generator.h"
#include "finders.h"
#include "biomes.h"
#include "util.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <inttypes.h>
#include <stdint.h>

static Generator g;
static int ready;
static int cur_dim = 0;
static uint64_t cur_seed = 0;
static int seeded;
static int *cache;
static size_t cache_len;

static void ensure_gen(void)
{
    if (!ready) {
        setupGenerator(&g, MC_1_21, 0);
        ready = 1;
        seeded = 0;
        cache_len = getMinCacheSize(&g, 4, 1, 1, 1);
        cache = (int *) malloc(cache_len * sizeof(int));
        if (!cache) {
            fputs("ERR oom\n", stdout);
            fflush(stdout);
            exit(1);
        }
    }
}

static void ensure_seed(int dim, uint64_t seed)
{
    ensure_gen();
    if (!seeded || cur_dim != dim || cur_seed != seed) {
        applySeed(&g, dim, seed);
        cur_dim = dim;
        cur_seed = seed;
        seeded = 1;
    }
}

static int sample_quart(int64_t seed_s, int dim, int qx, int qy, int qz)
{
    ensure_seed(dim, (uint64_t) seed_s);
    Range r = {4, qx, qz, 1, 1, qy, 1};
    if (genBiomes(&g, cache, r) != 0)
        return none;
    return cache[0];
}

static int sample_block(int64_t seed_s, int dim, int bx, int by, int bz)
{
    return sample_quart(seed_s, dim, bx >> 2, by >> 2, bz >> 2);
}

/* Returns 1 and fills chunk coords on success. */
static int struct_fsg_hit(uint64_t seed, int *pcx, int *pcz, int *vcx, int *vcz,
        int *bcx, int *bcz, int *fcx, int *fcz)
{
    Pos portal, vil;
    if (!getStructurePos(Ruined_Portal, MC_1_21, seed, 0, 0, &portal))
        return 0;
    *pcx = portal.x >> 4;
    *pcz = portal.z >> 4;
    if (*pcx < 0 || *pcx > 3 || *pcz < 0 || *pcz > 3)
        return 0;

    if (!getStructurePos(Village, MC_1_21, seed, 0, 0, &vil))
        return 0;
    *vcx = vil.x >> 4;
    *vcz = vil.z >> 4;
    if (*vcx < 0 || *vcx > 6 || *vcz < 0 || *vcz > 6)
        return 0;

    ensure_seed(DIM_NETHER, seed);

    StructureConfig sc;
    if (!getStructureConfig(Fortress, MC_1_21, &sc))
        return 0;

    Pos bestBast = {0, 0};
    Pos bestFort = {0, 0};
    int64_t bestBd = INT64_MAX;
    int found = 0;

    for (int rx = -1; rx <= 0; rx++) {
        for (int rz = -1; rz <= 0; rz++) {
            Pos bast;
            if (!getStructurePos(Bastion, MC_1_21, seed, rx, rz, &bast))
                continue;
            if (!isViableStructurePos(Bastion, &g, bast.x, bast.z, 0))
                continue;
            int64_t bd = (int64_t) bast.x * bast.x + (int64_t) bast.z * bast.z;
            if (bd > 100LL * 100)
                continue;

            int brx = floordiv(bast.x, sc.regionSize << 4);
            int brz = floordiv(bast.z, sc.regionSize << 4);
            Pos fortNear = {0, 0};
            int64_t bestFd = INT64_MAX;
            int foundF = 0;
            for (int frx = brx - 1; frx <= brx + 1; frx++) {
                for (int frz = brz - 1; frz <= brz + 1; frz++) {
                    Pos fort;
                    if (!getStructurePos(Fortress, MC_1_21, seed, frx, frz, &fort))
                        continue;
                    if (!isViableStructurePos(Fortress, &g, fort.x, fort.z, 0))
                        continue;
                    int64_t dx = (int64_t) fort.x - bast.x;
                    int64_t dz = (int64_t) fort.z - bast.z;
                    int64_t fd = dx * dx + dz * dz;
                    if (fd <= 160LL * 160 && fd < bestFd) {
                        bestFd = fd;
                        fortNear = fort;
                        foundF = 1;
                    }
                }
            }
            if (!foundF)
                continue;
            /* Prefer closer-to-origin bastion among those with a nearby fortress. */
            if (bd < bestBd) {
                bestBd = bd;
                bestBast = bast;
                bestFort = fortNear;
                found = 1;
            }
        }
    }
    if (!found)
        return 0;

    *bcx = bestBast.x >> 4;
    *bcz = bestBast.z >> 4;
    *fcx = bestFort.x >> 4;
    *fcz = bestFort.z >> 4;
    return 1;
}

int main(void)
{
    char line[512];
    setvbuf(stdout, NULL, _IOFBF, 1 << 20);
    setvbuf(stdin, NULL, _IOFBF, 1 << 20);
    fputs("READY\n", stdout);
    fflush(stdout);

    while (fgets(line, sizeof(line), stdin)) {
        if (strncmp(line, "QUIT", 4) == 0)
            break;

        if (strncmp(line, "SEEDSXZ ", 8) == 0) {
            int n = 0, dim = 0, by = 0;
            if (sscanf(line + 8, "%d %d %d", &n, &dim, &by) != 3
                    || n <= 0 || n > 1000000) {
                fputs("ERR bad_seedsxz\n", stdout);
                fflush(stdout);
                continue;
            }
            int qy = by >> 2;
            for (int i = 0; i < n; i++) {
                if (!fgets(line, sizeof(line), stdin)) {
                    fputs("ERR premature_eof\n", stdout);
                    fflush(stdout);
                    return 1;
                }
                int64_t seed_s;
                int cx, cz;
                if (sscanf(line, "%" SCNd64 " %d %d", &seed_s, &cx, &cz) != 3) {
                    fputs("ERR bad_seedxz\n", stdout);
                    continue;
                }
                int id = sample_quart(seed_s, dim, (cx << 4) >> 2, qy, (cz << 4) >> 2);
                printf("%d\n", id);
            }
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "SEEDS ", 6) == 0) {
            int n = 0, dim = 0, bx = 0, by = 0, bz = 0;
            if (sscanf(line + 6, "%d %d %d %d %d", &n, &dim, &bx, &by, &bz) != 5
                    || n <= 0 || n > 1000000) {
                fputs("ERR bad_seeds\n", stdout);
                fflush(stdout);
                continue;
            }
            int qx = bx >> 2, qy = by >> 2, qz = bz >> 2;
            for (int i = 0; i < n; i++) {
                if (!fgets(line, sizeof(line), stdin)) {
                    fputs("ERR premature_eof\n", stdout);
                    fflush(stdout);
                    return 1;
                }
                int64_t seed_s;
                if (sscanf(line, "%" SCNd64, &seed_s) != 1) {
                    fputs("ERR bad_seed\n", stdout);
                    continue;
                }
                int id = sample_quart(seed_s, dim, qx, qy, qz);
                printf("%d\n", id);
            }
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "BATCH ", 6) == 0) {
            int n = 0;
            if (sscanf(line + 6, "%d", &n) != 1 || n <= 0 || n > 1000000) {
                fputs("ERR bad_batch\n", stdout);
                fflush(stdout);
                continue;
            }
            for (int i = 0; i < n; i++) {
                if (!fgets(line, sizeof(line), stdin)) {
                    fputs("ERR premature_eof\n", stdout);
                    fflush(stdout);
                    return 1;
                }
                int64_t seed_s;
                int dim, x, y, z;
                if (sscanf(line, "%" SCNd64 " %d %d %d %d", &seed_s, &dim, &x, &y, &z) != 5) {
                    fputs("ERR bad_args\n", stdout);
                    continue;
                }
                int id = sample_block(seed_s, dim, x, y, z);
                const char *name = biome2str(MC_1_21, id);
                if (!name)
                    name = "unknown";
                printf("OK %d %s\n", id, name);
            }
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "VILLAGE ", 8) == 0) {
            uint64_t seed = 0;
            if (sscanf(line + 8, "%" SCNu64, &seed) != 1) {
                fputs("ERR bad_village\n", stdout);
                fflush(stdout);
                continue;
            }
            Pos pos;
            if (!getStructurePos(Village, MC_1_21, seed, 0, 0, &pos)) {
                fputs("FAIL\n", stdout);
                fflush(stdout);
                continue;
            }
            ensure_seed(0, seed);
            int viable = isViableStructurePos(Village, &g, pos.x, pos.z, 0);
            if (!viable) {
                fputs("FAIL\n", stdout);
                fflush(stdout);
                continue;
            }
            /* viable is often the matching village biome id on 1.18+ */
            printf("OK %d %d %d\n", pos.x, pos.z, viable);
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "VILLAGEPOS ", 11) == 0) {
            uint64_t seed = 0;
            if (sscanf(line + 11, "%" SCNu64, &seed) != 1) {
                fputs("ERR bad_villagepos\n", stdout);
                fflush(stdout);
                continue;
            }
            Pos pos;
            if (!getStructurePos(Village, MC_1_21, seed, 0, 0, &pos)) {
                fputs("FAIL\n", stdout);
                fflush(stdout);
                continue;
            }
            printf("OK %d %d\n", pos.x, pos.z);
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "FSGCHECK ", 9) == 0) {
            int n = 0, pbx = 0, pby = 0, pbz = 0, vx = 0, vz = 0;
            if (sscanf(line + 9, "%d %d %d %d %d %d", &n, &pbx, &pby, &pbz, &vx, &vz) != 6
                    || n <= 0 || n > 1000000) {
                fputs("ERR bad_fsgcheck\n", stdout);
                fflush(stdout);
                continue;
            }
            int pqx = pbx >> 2, pqy = pby >> 2, pqz = pbz >> 2;
            for (int i = 0; i < n; i++) {
                if (!fgets(line, sizeof(line), stdin)) {
                    fputs("ERR premature_eof\n", stdout);
                    fflush(stdout);
                    return 1;
                }
                int64_t seed_s;
                if (sscanf(line, "%" SCNd64, &seed_s) != 1) {
                    fputs("ERR bad_seed\n", stdout);
                    continue;
                }
                ensure_seed(0, (uint64_t) seed_s);
                /* Village rejects more than portal biome — check it first (same applySeed). */
                int viable = isViableStructurePos(Village, &g, vx, vz, 0);
                if (!viable) {
                    printf("0 0\n");
                    continue;
                }
                int portalId = sample_quart(seed_s, 0, pqx, pqy, pqz);
                printf("1 %d\n", portalId);
            }
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "SPAWN ", 6) == 0) {
            uint64_t seed = 0;
            if (sscanf(line + 6, "%" SCNu64, &seed) != 1) {
                fputs("ERR bad_spawn\n", stdout);
                fflush(stdout);
                continue;
            }
            ensure_seed(0, seed);
            Pos spawn = getSpawn(&g);
            printf("OK %d %d\n", spawn.x, spawn.z);
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "STRUCTFSGBATCH ", 15) == 0) {
            int n = 0;
            if (sscanf(line + 15, "%d", &n) != 1 || n <= 0 || n > 1000000) {
                fputs("ERR bad_structfsgbatch\n", stdout);
                fflush(stdout);
                continue;
            }
            for (int i = 0; i < n; i++) {
                if (!fgets(line, sizeof(line), stdin)) {
                    fputs("ERR premature_eof\n", stdout);
                    fflush(stdout);
                    return 1;
                }
                uint64_t seed = 0;
                if (sscanf(line, "%" SCNu64, &seed) != 1) {
                    fputs("ERR bad_seed\n", stdout);
                    continue;
                }
                int pcx, pcz, vcx, vcz, bcx, bcz, fcx, fcz;
                if (struct_fsg_hit(seed, &pcx, &pcz, &vcx, &vcz, &bcx, &bcz, &fcx, &fcz))
                    printf("1 %d %d %d %d %d %d %d %d\n", pcx, pcz, vcx, vcz, bcx, bcz, fcx, fcz);
                else
                    fputs("0\n", stdout);
            }
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "STRUCTFSG ", 10) == 0) {
            uint64_t seed = 0;
            if (sscanf(line + 10, "%" SCNu64, &seed) != 1) {
                fputs("ERR bad_structfsg\n", stdout);
                fflush(stdout);
                continue;
            }
            int pcx, pcz, vcx, vcz, bcx, bcz, fcx, fcz;
            if (struct_fsg_hit(seed, &pcx, &pcz, &vcx, &vcz, &bcx, &bcz, &fcx, &fcz))
                printf("OK %d %d %d %d %d %d %d %d\n", pcx, pcz, vcx, vcz, bcx, bcz, fcx, fcz);
            else
                fputs("FAIL\n", stdout);
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "BIOME ", 6) != 0) {
            fputs("ERR unknown_command\n", stdout);
            fflush(stdout);
            continue;
        }
        int64_t seed_s;
        int dim, x, y, z;
        if (sscanf(line + 6, "%" SCNd64 " %d %d %d %d", &seed_s, &dim, &x, &y, &z) != 5) {
            fputs("ERR bad_args\n", stdout);
            fflush(stdout);
            continue;
        }
        int id = sample_block(seed_s, dim, x, y, z);
        const char *name = biome2str(MC_1_21, id);
        if (!name)
            name = "unknown";
        printf("OK %d %s\n", id, name);
        fflush(stdout);
    }
    free(cache);
    return 0;
}
