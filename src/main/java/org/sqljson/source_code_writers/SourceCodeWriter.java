package org.sqljson.source_code_writers;

import java.io.IOException;
import java.util.List;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.sqljson.QueryReprSqlPath;
import org.sqljson.result_types.ResultType;


public interface SourceCodeWriter
{
   void writeQuerySourceCode
      (
         String queryName,
         List<ResultType> resultTypes,
         List<String> paramNames,
         List<QueryReprSqlPath> sqlPaths,
         String queryFileHeader,
         boolean includeTimestamp
      )
      throws IOException;
}

