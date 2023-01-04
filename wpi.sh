#!/bin/sh

# This script is a template for the WPI loop for a project with -Ainfer=ajava
# added to its build file. Fill in the variables at the beginning of the
# script with values that make sense for your project; the values there
# now are examples.

# Where should the output be placed at the end? This directory is also
# used to store intermediate WPI results. The directory does not need to
# exist. If it does exist when this script starts, it will be deleted.
WPITEMPDIR=/tmp/WPITEMP-DAGEN

# Where is WPI's output placed by the Checker Framework? This is some
# directory ending in build/whole-program-inference. For most projects,
# this directory is just ./build/whole-program-inference.
# This example is the output directory when running via the gradle plugin.
WPIOUTDIR=~/Documents/CFResearch/experiments-live/dagen-wpi/build/whole-program-inference

# The compile and clean commands for the project's build system.
BUILD_CMD="mvn compile"
CLEAN_CMD="mvn clean"

# Whether to run in debug mode.
DEBUG=1

# End of variables. You probably don't need to make changes below this line.

rm -rf ${WPITEMPDIR}
mkdir ${WPITEMPDIR}

while : ; do
    if [[ ${DEBUG} == 1 ]]; then
	echo "entering a new iteration"
    fi
    ${BUILD_CMD}
    ${CLEAN_CMD}
    [[ $(diff -r ${WPITEMPDIR} ${WPIOUTDIR}) ]] || break
    rm -rf ${WPITEMPDIR}
    mv ${WPIOUTDIR} ${WPITEMPDIR}
done
