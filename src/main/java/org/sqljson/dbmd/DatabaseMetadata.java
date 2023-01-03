package org.sqljson.dbmd;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import static org.sqljson.dbmd.CaseSensitivity.INSENSITIVE_STORED_LOWER;
import static org.sqljson.dbmd.CaseSensitivity.INSENSITIVE_STORED_UPPER;
import static org.sqljson.util.Nullables.valueOr;

public class DatabaseMetadata
{
   private final String dbmsName;

   private final String dbmsVersion;

   private final CaseSensitivity caseSensitivity;

   private final List<RelMetadata> relationMetadatas;

   private final List<ForeignKey> foreignKeys;


   private static final Predicate<String> lc_ = Pattern.compile("^[a-z_]+$").asPredicate();
   private static final Predicate<String> uc_ = Pattern.compile("^[A-Z_]+$").asPredicate();

   // derived data
   // Access these only via methods of the same name, which make sure these fields are initialized.
   private Map<RelId, RelMetadata> relMDsByRelId;

   private Map<RelId, List<ForeignKey>> fksByParentRelId;

   private Map<RelId, List<ForeignKey>> fksByChildRelId;

   public DatabaseMetadata
      (
         List<RelMetadata> relationMetadatas,
         List<ForeignKey> foreignKeys,
         CaseSensitivity caseSensitivity,
         String dbmsName,
         String dbmsVersion
      )
   {
      this.relationMetadatas = sortedMds(requireNonNull(relationMetadatas));
      this.foreignKeys = sortedFks(requireNonNull(foreignKeys));
      this.caseSensitivity = requireNonNull(caseSensitivity);
      this.dbmsName = requireNonNull(dbmsName);
      this.dbmsVersion = requireNonNull(dbmsVersion);
   }

   DatabaseMetadata()
   {
      relationMetadatas = emptyList();
      foreignKeys = emptyList();
      caseSensitivity = INSENSITIVE_STORED_LOWER;
      dbmsName = "";
      dbmsVersion = "";
   }

   public List<RelMetadata> getRelationMetadatas() { return relationMetadatas; }

   public List<ForeignKey> getForeignKeys() { return foreignKeys; }

   public CaseSensitivity getCaseSensitivity() { return caseSensitivity; }

   public String getDbmsName() { return dbmsName; }

   public String getDbmsVersion() { return dbmsVersion; }

   public RelMetadata getRelationMetadata(RelId relId)
   {
      return relMDsByRelId().get(relId);
   }

   public List<String> getPrimaryKeyFieldNames
      (
         RelId relId,
         String alias
      )
   {
      RelMetadata relMd = requireNonNull(getRelationMetadata(relId));

      return relMd.getPrimaryKeyFieldNames(alias);
   }

   public List<String> getPrimaryKeyFieldNames(RelId relId)
   {
      return getPrimaryKeyFieldNames(relId, null);
   }

   public List<ForeignKey> getForeignKeysFromTo
      (
         RelId childRelId,
         RelId parentRelId,
         ForeignKeyScope fkScope
      )
   {
      List<ForeignKey> res = new ArrayList<>();

      if ( childRelId == null && parentRelId == null )
         res.addAll(foreignKeys);
      else if ( childRelId != null && parentRelId != null )
      {
         res.addAll(fksByChildRelId(childRelId));
         res.retainAll(fksByParentRelId(parentRelId));
      }
      else
         res.addAll(childRelId != null ? fksByChildRelId(childRelId) : fksByParentRelId(requireNonNull(parentRelId)));

      if ( fkScope == ForeignKeyScope.REGISTERED_TABLES_ONLY )
      {
         return
            res.stream()
            .filter(fk ->
               getRelationMetadata(fk.getForeignKeyRelationId()) != null &&
               getRelationMetadata(fk.getPrimaryKeyRelationId()) != null
            )
            .collect(toList());
      }
      else
         return res;
   }

   /** Return a single foreign key between the passed tables, having the specified field names if specified,
    *  or null if not found. IllegalArgumentException is thrown if multiple foreign keys satisfy the requirements.
    */
   public ForeignKey getForeignKeyFromTo
      (
         RelId fromRelId,
         RelId toRelId,
         Set<String> fieldNames,
         ForeignKeyScope fkScope
      )
   {
      ForeignKey soughtFk = null;
      Set<String> normdFkFieldNames = fieldNames != null ? normalizeNames(fieldNames) : null;

      for ( ForeignKey fk : getForeignKeysFromTo(fromRelId, toRelId, fkScope) )
      {
         if ( normdFkFieldNames == null || fk.foreignKeyFieldNamesSetEquals(normdFkFieldNames) )
         {
            if ( soughtFk != null ) // already found an fk satisfying requirements?
               throw new IllegalArgumentException(
                  "Child table " + fromRelId + " has multiple foreign keys to parent table " + toRelId +
                  (fieldNames != null ?
                  " with the same specified source fields."
                  : " and no foreign key fields were specified to disambiguate.")
               );

            soughtFk = fk;
            // No breaking from the loop here, so case that multiple fk's satisfy requirements can be detected.
         }
      }

      return soughtFk;
   }

   /////////////////////////////////////////////////////////
   // Sorting for deterministic output

   private static List<RelMetadata> sortedMds(List<RelMetadata> relMds)
   {
      List<RelMetadata> rmds = new ArrayList<>(relMds);

      rmds.sort(Comparator.comparing(rmd -> rmd.getRelationId().getIdString()));

      return unmodifiableList(rmds);
   }

   /**
    * Return a new copy of the input list, with its foreign keys sorted by source and target relation names and source and target field names.
    */
   private static List<ForeignKey> sortedFks(List<ForeignKey> foreignKeys)
   {
      List<ForeignKey> fks = new ArrayList<>(foreignKeys);

      fks.sort((fk1, fk2) -> {
         int srcRelComp = fk1.getForeignKeyRelationId().getIdString().compareTo(fk2.getForeignKeyRelationId().getIdString());
         if (srcRelComp != 0)
            return srcRelComp;

         int tgtRelComp = fk1.getPrimaryKeyRelationId().getIdString().compareTo(fk2.getPrimaryKeyRelationId().getIdString());
         if (tgtRelComp != 0)
            return tgtRelComp;

         int srcFieldsComp = compareStringListsLexicographically(fk1.getChildFieldNames(), fk2.getChildFieldNames());

         if (srcFieldsComp != 0)
            return srcFieldsComp;
         else
            return compareStringListsLexicographically(fk1.getParentFieldNames(), fk2.getParentFieldNames());
      });

      return unmodifiableList(fks);
   }

   private static int compareStringListsLexicographically
      (
         List<String> strs1,
         List<String> strs2
      )
   {
      int commonCount = Math.min(strs1.size(), strs2.size());

      for ( int i = 0; i < commonCount; ++i )
      {
         int comp = strs1.get(i).compareTo(strs2.get(i));
         if (comp != 0)
            return comp;
      }

      return Integer.compare(strs1.size(), strs2.size());
   }

   // Sorting for deterministic output
   /////////////////////////////////////////////////////////


   /////////////////////////////////////////////////////////
   // Derived data accessor methods

   private Map<RelId, RelMetadata> relMDsByRelId()
   {
      if ( relMDsByRelId == null )
         initDerivedData();

      return requireNonNull(relMDsByRelId);
   }

   private List<ForeignKey> fksByParentRelId(RelId relId)
   {
      if ( fksByParentRelId == null )
         initDerivedData();

      return valueOr(requireNonNull(fksByParentRelId).get(relId), emptyList());
   }

   private List<ForeignKey> fksByChildRelId(RelId relId)
   {
      if ( fksByChildRelId == null )
         initDerivedData();

      return valueOr(requireNonNull(fksByChildRelId).get(relId), emptyList());
   }

   private void initDerivedData()
   {
      relMDsByRelId = new HashMap<>();
      fksByParentRelId = new HashMap<>();
      fksByChildRelId = new HashMap<>();

      for ( RelMetadata relMd : relationMetadatas)
         relMDsByRelId.put(relMd.getRelationId(), relMd);

      for ( ForeignKey fk : foreignKeys )
      {
         RelId srcRelId = fk.getForeignKeyRelationId();
         RelId tgtRelId = fk.getPrimaryKeyRelationId();

         List<ForeignKey> fksFromChild = fksByChildRelId.computeIfAbsent(srcRelId, k -> new ArrayList<>());
         fksFromChild.add(fk);

         List<ForeignKey> fksToParent = fksByParentRelId.computeIfAbsent(tgtRelId, k -> new ArrayList<>());
         fksToParent.add(fk);
      }
   }

   // Derived data accessor methods
   /////////////////////////////////////////////////////////

   /// Quote a database identifier only if it would be interpreted differently if quoted.
   public String quoteIfNeeded(String id)
   {
      if ( id.startsWith("\"") && id.endsWith("\"") )
         return id;
      if ( id.startsWith("_") )
         return "\"" + id + "\"";
      if ( caseSensitivity == INSENSITIVE_STORED_LOWER && lc_.test(id) )
         return id;
      if ( caseSensitivity == INSENSITIVE_STORED_UPPER && uc_.test(id) )
         return id;
      return "\"" + id + "\"";
   }

   // Normalize a database object name.
   public String normalizeName(String id)
   {
      if ( id.startsWith("\"") && id.endsWith("\"") )
         return id;
      else if ( caseSensitivity == INSENSITIVE_STORED_LOWER )
         return id.toLowerCase();
      else if ( caseSensitivity == INSENSITIVE_STORED_UPPER )
         return id.toUpperCase();
      else
         return id;
   }

   private Set<String> normalizeNames(Set<String> names)
   {
      return names.stream().map(this::normalizeName).collect(toSet());
   }

   /// Make a relation id from a given qualified or unqualified table identifier
   /// and an optional default schema for interpreting unqualified table names.
   /// The table is not verified to exist in the metadata.
   public RelId toRelId(String table, String defaultSchema)
   {
      int dotIx = table.indexOf('.');

      if ( dotIx != -1 ) // already qualified, split it
         return new
            RelId(
               normalizeName(table.substring(dotIx+1)),
               normalizeName(table.substring(0, dotIx))
            );
      else // not qualified, qualify it if there is a default schema
         return new RelId(defaultSchema != null ? normalizeName(defaultSchema) : null, normalizeName(table));
   }
}

