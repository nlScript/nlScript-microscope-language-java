#!/bin/bash

# https://medium.com/simform-engineering/publishing-library-in-maven-central-part-2-515c5d54f566
# https://central.sonatype.org/register/central-portal/

if ! mkdir repo/; then
    echo "Exiting..."
    exit -1
fi

name="nlScript-microscope-language"

version=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`

if ! [[ $version =~ [0-9]+\.[0-9]+\.[0-9]+ ]]; then
    echo "Strange version string: $version"
    echo "Exiting..."
    exit -1
fi



# Temporarily commented out, can be used again once nlScript is on maven central
# mvn clean -DskipTests install -Dmaven.repo.local=./repo

# For now, use the following commands
mvn clean
(cd ../nlScript-java && mvn -DskipTests install -Dmaven.repo.local=../nlScript-microscope-language-java/repo)
mvn -DskipTests install -Dmaven.repo.local=./repo

cd "repo/io/github/nlscript/$name/$version/"

rm "$name-$version-tests.jar"
rm _remote.repositories

gpg -ab "$name-$version.pom"
gpg -ab "$name-$version.jar"
gpg -ab "$name-$version-sources.jar"
gpg -ab "$name-$version-javadoc.jar"

md5sum.exe "$name-$version.pom"         | cut -d' ' -f 1 > "$name-$version.pom.md5"
md5sum.exe "$name-$version.jar"         | cut -d' ' -f 1 > "$name-$version.jar.md5"
md5sum.exe "$name-$version-sources.jar" | cut -d' ' -f 1 > "$name-$version-sources.jar.md5"
md5sum.exe "$name-$version-javadoc.jar" | cut -d' ' -f 1 > "$name-$version-javadoc.jar.md5"

sha1sum.exe "$name-$version.pom"         | cut -d' ' -f 1 > "$name-$version.pom.sha1"
sha1sum.exe "$name-$version.jar"         | cut -d' ' -f 1 > "$name-$version.jar.sha1"
sha1sum.exe "$name-$version-sources.jar" | cut -d' ' -f 1 > "$name-$version-sources.jar.sha1"
sha1sum.exe "$name-$version-javadoc.jar" | cut -d' ' -f 1 > "$name-$version-javadoc.jar.sha1"

cd ../../../../../

zip "../release-bundle-$version.zip" "io/github/nlscript/$name/$version/*"

echo "Don't forget to delete the temporary repository 'repo'"
