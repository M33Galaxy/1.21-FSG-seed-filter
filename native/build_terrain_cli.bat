@REM Build cubiomes_terrain_cli.exe against c:\Cubiomes\cubiomes-master (terrainnoise).
@REM Objects go to native\terrain_build\ so biome CLI objects are not overwritten.
@echo off
setlocal
set SRC=c:\Cubiomes\cubiomes-master
set OUT=%~dp0
set BUILD=%OUT%terrain_build
set GCC=C:\msys64\ucrt64\bin\gcc.exe
if not exist "%GCC%" (
  echo gcc not found at %GCC%
  exit /b 1
)
if not exist "%SRC%\terrainnoise.c" (
  echo terrainnoise.c not found under %SRC%
  exit /b 1
)
mkdir "%BUILD%" 2>nul
cd /d "%BUILD%"
"%GCC%" -O3 -fwrapv -c "%SRC%\biomes.c" -I "%SRC%" -o biomes.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\biomenoise.c" -I "%SRC%" -o biomenoise.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\generator.c" -I "%SRC%" -o generator.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\layers.c" -I "%SRC%" -o layers.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\noise.c" -I "%SRC%" -o noise.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\util.c" -I "%SRC%" -o util.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\terrainnoise.c" -I "%SRC%" -o terrainnoise.o || exit /b 1
"%GCC%" -O3 -fwrapv -I "%SRC%" "%OUT%cubiomes_terrain_cli.c" biomes.o biomenoise.o generator.o layers.o noise.o util.o terrainnoise.o -o "%OUT%cubiomes_terrain_cli.exe" -lm || exit /b 1
echo built %OUT%cubiomes_terrain_cli.exe
