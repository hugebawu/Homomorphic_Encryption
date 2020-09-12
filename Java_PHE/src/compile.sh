#!/bin/bash

# Only run from Root!
javac -cp ".:../libs/bcprov-ext-jdk15on-162.jar" -Xlint -sourcepath "." Main.java 

# Run the program
java -cp "." Main $1
