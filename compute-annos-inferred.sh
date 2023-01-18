#!/bin/sh

# This script uses runs the AnnotationStatistics utility of the Checker
# Framework on the results of WPI on each checker for which annotations
# were inferred. It should be run on the wpi-annotations branch.
# You should make a copy of this script for each project being analyzed
# and modify it so that it works on that project - this is just a template.

### you may need to change these constants, depending on the project:

# the root of the wpi output
WPI_RESULTS_DIR=./wpi-annotations/

# the root of the java source files
JAVA_SRC_DIR=./src/main/java/

# the command to run AnnotationStatistics (should be the same command
# that is used to compile the target program)
RUN_ANNO_STATS="$JAVA_HOME/bin/javac -d /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/target/classes -classpath /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/target/classes:/home/dan/.m2/repository/com/fasterxml/jackson/core/jackson-databind/2.14.0-rc1/jackson-databind-2.14.0-rc1.jar:/home/dan/.m2/repository/com/fasterxml/jackson/core/jackson-annotations/2.14.0-rc1/jackson-annotations-2.14.0-rc1.jar:/home/dan/.m2/repository/com/fasterxml/jackson/core/jackson-core/2.14.0-rc1/jackson-core-2.14.0-rc1.jar:/home/dan/.m2/repository/com/fasterxml/jackson/dataformat/jackson-dataformat-yaml/2.14.0-rc1/jackson-dataformat-yaml-2.14.0-rc1.jar:/home/dan/.m2/repository/org/yaml/snakeyaml/1.32/snakeyaml-1.32.jar:/home/dan/.m2/repository/com/fasterxml/jackson/datatype/jackson-datatype-jdk8/2.14.0-rc1/jackson-datatype-jdk8-2.14.0-rc1.jar:/home/dan/.m2/repository/com/fasterxml/jackson/module/jackson-module-jsonSchema/2.14.0-rc1/jackson-module-jsonSchema-2.14.0-rc1.jar:/home/dan/.m2/repository/javax/validation/validation-api/1.1.0.Final/validation-api-1.1.0.Final.jar:/home/dan/.m2/repository/org/postgresql/postgresql/42.4.3/postgresql-42.4.3.jar:/home/dan/.m2/repository/com/oracle/ojdbc/ojdbc8/19.3.0.0/ojdbc8-19.3.0.0.jar:/home/dan/.m2/repository/com/oracle/ojdbc/ucp/19.3.0.0/ucp-19.3.0.0.jar:/home/dan/.m2/repository/com/oracle/ojdbc/oraclepki/19.3.0.0/oraclepki-19.3.0.0.jar:/home/dan/.m2/repository/com/oracle/ojdbc/osdt_cert/19.3.0.0/osdt_cert-19.3.0.0.jar:/home/dan/.m2/repository/com/oracle/ojdbc/osdt_core/19.3.0.0/osdt_core-19.3.0.0.jar:/home/dan/.m2/repository/com/oracle/ojdbc/simplefan/19.3.0.0/simplefan-19.3.0.0.jar:/home/dan/.m2/repository/com/oracle/ojdbc/ons/19.3.0.0/ons-19.3.0.0.jar:/home/dan/.m2/repository/org/checkerframework/checker-qual/3.1.1/checker-qual-3.1.1.jar:/home/dan/.m2/repository/com/google/code/findbugs/annotations/3.0.1/annotations-3.0.1.jar:/home/dan/.m2/repository/net/jcip/jcip-annotations/1.0/jcip-annotations-1.0.jar:/home/dan/.m2/repository/com/google/code/findbugs/jsr305/3.0.1/jsr305-3.0.1.jar: -sourcepath /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java:/home/dan/Documents/CFResearch/experiments-live/dagen-wpi/target/generated-sources/annotations: /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/SpecLocation.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/RelDescr.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/source_writers/SourceCodeWriter.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/TableFieldExpr.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/source_code_writers/JavaWriter.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/ChildCollectionSpec.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/AppUtils.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/sql_dialects/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/QueryGeneratorMain.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/StringFuns.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/DatabaseMetadataFetcher.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/SimpleTableFieldProperty.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/source_code_writers/TypeScriptWriter.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/sql_dialects/OracleDialect.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/ParentSpec.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/source_code_writers/SourceCodeLanguage.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/source_code_writers/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/ParentReferenceProperty.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/RecordCondition.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/ChildCollectionProperty.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/DatabaseMetadataGeneratorMain.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/Nullables.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/SpecError.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/QueryGroupSpec.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/source_code_writers/SourceCodeWriter.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/source_writers/JavaWriter.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/ResultTypeBuilder.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/ResultRepr.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/RelMetadata.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/ForeignKey.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/TableJsonSpec.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/ResultType.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/QueryReprSqlPath.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/RelId.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/TableExpressionProperty.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/IO.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/sql_dialects/PostgresDialect.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/DatabaseMetadata.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/PropertyNameDefault.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/Field.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/QuerySpec.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/CaseSensitivity.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/RelMetadataBuilder.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/ForeignKeyBuilder.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/RelationMetadataSourceGeneratorMain.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/sql_dialects/SqlDialect.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/result_types/ResultTypesGenerator.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/ForeignKeyScope.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/source_writers/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/QuerySqlGenerator.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/Serialization.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/CustomJoinCondition.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/package-info.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/query_specs/QuerySpecValidations.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/util/Props.java /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/src/main/java/org/sqljson/dbmd/source_writers/TypeScriptWriter.java -s /home/dan/Documents/CFResearch/experiments-live/dagen-wpi/target/generated-sources/annotations -processor org.checkerframework.common.util.count.AnnotationStatistics -processorpath /home/dan/.m2/repository/org/checkerframework/checker/3.28.1-SNAPSHOT/checker-3.28.1-SNAPSHOT.jar:/home/dan/.m2/repository/org/checkerframework/checker-qual/3.28.1-SNAPSHOT/checker-qual-3.28.1-SNAPSHOT.jar:/home/dan/.m2/repository/org/checkerframework/checker-util/3.28.1-SNAPSHOT/checker-util-3.28.1-SNAPSHOT.jar: -g --release 11 -encoding UTF-8 -Xmaxerrs 10000 -Xmaxwarns 10000 -Aannotations -Anolocations -Aannotationserror -Alint"

### no need to make changes below this line, usually

# TODO: compute this list from the names of the ajava files
AJAVA_FILE_LIST=$(cd ${WPI_RESULTS_DIR} && find . -name "*-*.ajava")
CHECKER_LIST=""

if [ -f compute-annos-inferred-out ]; then
    # from https://betterprogramming.pub/24-bashism-to-avoid-for-posix-compliant-shell-scripts-8e7c09e0f49a, in response to a shellcheck error noting that $RANDOM is not POSIX-compliant
    var1=$(date +%s)
    var2=$$
    random="$var1$var2"
    echo "making a backup of compute-annos-inferred-out in compute-annos-inferred-out-${random}"
    mv compute-annos-inferred-out compute-annos-inferred-out-"${random}"
fi

for ajava_filename in ${AJAVA_FILE_LIST};
do
    tmp=${ajava_filename##*-};
    CHECKER_LIST="${CHECKER_LIST} ${tmp%.ajava}"
done
CHECKER_LIST=$(echo "${CHECKER_LIST}" | sort | uniq)

JAVA_FILES=$(cd ${JAVA_SRC_DIR} && find . -name "*.java")

for checker in ${CHECKER_LIST}; do
    # copy the ajava files generated for the relevant checker to the
    # corresponding places in the source tree
    echo "======== RUNNING ANNOTATION STATISTIC FOR INFERRED ANNOTATIONS FROM ${checker} ========"
    for java_file in ${JAVA_FILES}; do
	# strip the ".java" from the end of the file name
	base_filename="${java_file%.*}"
	ajava_file="${WPI_RESULTS_DIR}/${base_filename}-${checker}.ajava"
	if [ -f "${ajava_file}" ]; then
	    cp "${ajava_file}" "${JAVA_SRC_DIR}/${java_file}"
	fi
    done
    # run AnnotationStatistics and append the results to the output file
    eval "${RUN_ANNO_STATS}" >> compute-annos-inferred-out 2>&1
    # reset to the state before doing the copying
    git reset --hard wpi-annotations -- > /dev/null 2>&1
done

# find the lines from AnnotationStatistics listing annotations
echo "====== COMBINED RESULTS ======="
grep "^org.checkerframework" < compute-annos-inferred-out | sort | uniq
