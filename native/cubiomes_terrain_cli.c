/*
 * Long-running terrain CLI for ruined-portal Y (cubiomes terrainnoise).
 * Built against c:\Cubiomes\cubiomes-master.
 *
 * Protocol:
 *   SEED <u64>
 *   SURFACE <blockX> <blockZ>     → OK <worldSurfaceY>
 *   DIG <candY> <minY> <x0> <z0> <x1> <z1> <x2> <z2> <x3> <z3>
 *                                 → OK <projectedY>
 *   QUIT
 *
 * SURFACE / DIG approximate vanilla WORLD_SURFACE_WG for overworld ruined portals:
 * density solids + water filling from (solidTop+1)..(SEA_LEVEL-1) when the
 * column is below sea level (rivers/oceans). Without water, dig wrongly sinks
 * to the riverbed and crying RNG desyncs.
 */
#include "terrainnoise.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <inttypes.h>

#define COLUMN_CACHE_SIZE 256
#define NOISE_COLUMN_LEN (48 + 1)
#define WORLD_MIN_Y (-64)
/** Vanilla overworld seaLevel; top water block / WORLD_SURFACE portal Y is SEA_LEVEL-1. */
#define SEA_LEVEL 63
#define WATER_SURFACE_Y (SEA_LEVEL - 1)

typedef struct {
    int valid;
    int cell_x;
    int cell_z;
    double column[NOISE_COLUMN_LEN];
} ColumnCacheEntry;

static TerrainNoise tn;
static int ready;
static uint64_t cur_seed;
static ColumnCacheEntry cache[COLUMN_CACHE_SIZE];

static void clear_cache(void)
{
    memset(cache, 0, sizeof(cache));
}

static int ensure_seed(uint64_t seed)
{
    if (ready && cur_seed == seed)
        return 1;
    memset(&tn, 0, sizeof(tn));
    if (!setupTerrainNoise(&tn, MC_1_21, 0) || !initTerrainNoise(&tn, seed, DIM_OVERWORLD))
        return 0;
    cur_seed = seed;
    ready = 1;
    clear_cache();
    return 1;
}

static const double *get_noise_column(int cell_x, int cell_z)
{
    uint32_t h = (uint32_t) cell_x * 0x9e3779b1u ^ (uint32_t) cell_z * 0x85ebca6bu;
    uint32_t idx = h & (COLUMN_CACHE_SIZE - 1);
    for (uint32_t step = 0; step < COLUMN_CACHE_SIZE; ++step) {
        ColumnCacheEntry *e = &cache[(idx + step) & (COLUMN_CACHE_SIZE - 1)];
        if (e->valid && e->cell_x == cell_x && e->cell_z == cell_z)
            return e->column;
        if (!e->valid) {
            sampleNoiseColumn(&tn, cell_x, cell_z, e->column);
            e->valid = 1;
            e->cell_x = cell_x;
            e->cell_z = cell_z;
            return e->column;
        }
    }
    {
        static double fallback[NOISE_COLUMN_LEN];
        sampleNoiseColumn(&tn, cell_x, cell_z, fallback);
        return fallback;
    }
}

/** Density top-solid Y (riverbed / ground without aquifers). */
static int top_solid_y(int x, int z)
{
    int cell_x = x >> 2, cell_z = z >> 2;
    const double *ds00 = get_noise_column(cell_x + 0, cell_z + 0);
    const double *ds01 = get_noise_column(cell_x + 0, cell_z + 1);
    const double *ds10 = get_noise_column(cell_x + 1, cell_z + 0);
    const double *ds11 = get_noise_column(cell_x + 1, cell_z + 1);
    int blocks[384];
    int air_y = generateColumn(x, z, blocks, ds00, ds01, ds10, ds11, 1);
    return air_y - 64 - 1;
}

/**
 * WORLD_SURFACE_WG ≈ getBaseHeight-1 for ruined portals:
 * if ground is below sea level, top water is at SEA_LEVEL-1 (62).
 */
static int world_surface_y(int x, int z)
{
    int solid = top_solid_y(x, z);
    return solid < WATER_SURFACE_Y ? WATER_SURFACE_Y : solid;
}

static void fill_column_solids(int x, int z, int solids[384])
{
    int cell_x = x >> 2, cell_z = z >> 2;
    const double *ds00 = get_noise_column(cell_x + 0, cell_z + 0);
    const double *ds01 = get_noise_column(cell_x + 0, cell_z + 1);
    const double *ds10 = get_noise_column(cell_x + 1, cell_z + 0);
    const double *ds11 = get_noise_column(cell_x + 1, cell_z + 1);
    generateColumn(x, z, solids, ds00, ds01, ds10, ds11, 0);
}

static int world_solid(const int solids[384], int world_y)
{
    int idx = world_y - WORLD_MIN_Y;
    if (idx < 0 || idx >= 384)
        return 0;
    return solids[idx] != 0;
}

/**
 * WORLD_SURFACE opaque for dig: non-air ≈ solid OR aquifer water
 * filling (solidTop, WATER_SURFACE_Y] when solidTop < WATER_SURFACE_Y.
 */
static int world_surface_opaque(const int solids[384], int solid_top, int world_y)
{
    if (world_solid(solids, world_y))
        return 1;
    if (solid_top < WATER_SURFACE_Y && world_y > solid_top && world_y <= WATER_SURFACE_Y)
        return 1;
    return 0;
}

static int column_solid_top_from_solids(const int solids[384])
{
    for (int y = 319; y >= WORLD_MIN_Y; --y) {
        if (world_solid(solids, y))
            return y;
    }
    return WORLD_MIN_Y - 1;
}

/** Dig-down like RuinedPortalStructure.findSuitableY (WORLD_SURFACE_WG). */
static int dig_portal_y(int cand_y, int min_y,
        int x0, int z0, int x1, int z1, int x2, int z2, int x3, int z3)
{
    int c0[384], c1[384], c2[384], c3[384];
    fill_column_solids(x0, z0, c0);
    fill_column_solids(x1, z1, c1);
    fill_column_solids(x2, z2, c2);
    fill_column_solids(x3, z3, c3);
    int t0 = column_solid_top_from_solids(c0);
    int t1 = column_solid_top_from_solids(c1);
    int t2 = column_solid_top_from_solids(c2);
    int t3 = column_solid_top_from_solids(c3);

    int projected = cand_y;
    for (; projected > min_y; --projected) {
        int corners = 0;
        if (world_surface_opaque(c0, t0, projected)) corners++;
        if (world_surface_opaque(c1, t1, projected)) corners++;
        if (corners < 3 && world_surface_opaque(c2, t2, projected)) corners++;
        if (corners < 3 && world_surface_opaque(c3, t3, projected)) corners++;
        if (corners >= 3)
            break;
    }
    return projected;
}

int main(void)
{
    char line[512];
    setvbuf(stdout, NULL, _IOLBF, 1 << 16);
    setvbuf(stdin, NULL, _IOLBF, 1 << 16);
    fputs("READY\n", stdout);
    fflush(stdout);

    while (fgets(line, sizeof(line), stdin)) {
        if (strncmp(line, "QUIT", 4) == 0)
            break;

        if (strncmp(line, "SEED ", 5) == 0) {
            uint64_t seed = 0;
            if (sscanf(line + 5, "%" SCNu64, &seed) != 1 || !ensure_seed(seed)) {
                fputs("ERR seed\n", stdout);
                fflush(stdout);
                continue;
            }
            fputs("OK\n", stdout);
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "SURFACE ", 8) == 0) {
            int x, z;
            if (!ready || sscanf(line + 8, "%d %d", &x, &z) != 2) {
                fputs("ERR surface\n", stdout);
                fflush(stdout);
                continue;
            }
            printf("OK %d\n", world_surface_y(x, z));
            fflush(stdout);
            continue;
        }

        if (strncmp(line, "DIG ", 4) == 0) {
            int cand, miny, x0, z0, x1, z1, x2, z2, x3, z3;
            if (!ready || sscanf(line + 4, "%d %d %d %d %d %d %d %d %d %d",
                    &cand, &miny, &x0, &z0, &x1, &z1, &x2, &z2, &x3, &z3) != 10) {
                fputs("ERR dig\n", stdout);
                fflush(stdout);
                continue;
            }
            printf("OK %d\n", dig_portal_y(cand, miny, x0, z0, x1, z1, x2, z2, x3, z3));
            fflush(stdout);
            continue;
        }

        fputs("ERR unknown\n", stdout);
        fflush(stdout);
    }
    return 0;
}
