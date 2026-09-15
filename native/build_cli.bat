@REM Build cubiomes_biome_cli.exe (requires MSYS2 gcc).
@echo off
setlocal
set SRC=c:\Cubiomes-default\cubiomes-master
set OUT=%~dp0
set GCC=C:\msys64\ucrt64\bin\gcc.exe
if not exist "%GCC%" (
  echo gcc not found at %GCC%
  exit /b 1
)
cd /d "%OUT%"
"%GCC%" -O3 -fwrapv -c "%SRC%\biomes.c" -I "%SRC%" -o biomes.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\biomenoise.c" -I "%SRC%" -o biomenoise.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\generator.c" -I "%SRC%" -o generator.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\layers.c" -I "%SRC%" -o layers.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\noise.c" -I "%SRC%" -o noise.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\util.c" -I "%SRC%" -o util.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\finders.c" -I "%SRC%" -o finders.o || exit /b 1
"%GCC%" -O3 -fwrapv -c "%SRC%\quadbase.c" -I "%SRC%" -o quadbase.o || exit /b 1
"%GCC%" -O3 -fwrapv -I "%SRC%" cubiomes_biome_cli.c biomes.o biomenoise.o generator.o layers.o noise.o util.o finders.o quadbase.o -o cubiomes_biome_cli.exe -lm || exit /b 1
echo built %OUT%cubiomes_biome_cli.exe
