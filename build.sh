#!/bin/bash
echo ----------------------------------------------------------------------
echo Building...
echo Cleaning target folder
rm -rf mantarget
echo Create directory
mkdir mantarget
echo Copy libs
cp ./lib/*.jar ./mantarget
echo Compile
javac -cp lib/pi4j-core.jar -d mantarget ./src/main/java/mk/hsilomedus/pn532/*.java
echo Done!
echo ----------------------------------------------------------------------
