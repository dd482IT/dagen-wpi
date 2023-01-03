package org.sqljson.dbmd;

import java.io.Serializable;
import java.util.*;

import org.checkerframework.checker.nullness.qual.Nullable;

import static java.util.Collections.unmodifiableList;
import static java.util.Objects.requireNonNull;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class ForeignKey implements Serializable {

   private final String constraintName;

   private final RelId foreignKeyRelationId; // child/referencing table

   private final RelId primaryKeyRelationId; // parent/referenced table

   private final List<Component> foreignKeyComponents;

   public enum EquationStyle { SOURCE_ON_LEFTHAND_SIDE, TARGET_ON_LEFTHAND_SIDE }

   public ForeignKey
      (
         String constraintName,
         RelId foreignKeyRelationId,
         RelId primaryKeyRelationId,
         List<Component> foreignKeyComponents
      )
   {
      this.constraintName = constraintName;
      this.foreignKeyRelationId = requireNonNull(foreignKeyRelationId);
      this.primaryKeyRelationId = requireNonNull(primaryKeyRelationId);
      this.foreignKeyComponents = unmodifiableList(new ArrayList<>(requireNonNull(foreignKeyComponents)));
   }

   ForeignKey()
   {
      this.constraintName = null;
      this.foreignKeyRelationId = RelId.DUMMY_INSTANCE;
      this.primaryKeyRelationId = RelId.DUMMY_INSTANCE;
      this.foreignKeyComponents = Collections.emptyList();
   }

   public String getConstraintName() { return constraintName; }

   public RelId getForeignKeyRelationId() { return foreignKeyRelationId; }

   public RelId getPrimaryKeyRelationId() { return primaryKeyRelationId; }

   public List<Component> getForeignKeyComponents() { return foreignKeyComponents; }

   public List<String> getChildFieldNames()
   {
      List<String> names = new ArrayList<>();

      for ( Component comp: foreignKeyComponents )
         names.add(comp.getForeignKeyFieldName());

      return names;
   }

   public List<String> getParentFieldNames()
   {
      List<String> names = new ArrayList<>();

      for ( Component comp: foreignKeyComponents )
         names.add(comp.getPrimaryKeyFieldName());

      return names;
   }

   public String asEquation
      (
         String childRelAlias,
         String parentRelAlias
      )
   {
      return asEquation(childRelAlias, parentRelAlias, EquationStyle.SOURCE_ON_LEFTHAND_SIDE);
   }

   public String asEquation
      (
         String childRelAlias,
         String parentRelAlias,
         EquationStyle style
      )
   {
      StringBuilder sb = new StringBuilder();

      boolean srcFirst = style == EquationStyle.SOURCE_ON_LEFTHAND_SIDE;

      for ( Component fkc: foreignKeyComponents )
      {
         if ( sb.length() > 0 )
            sb.append(" and ");

         String fstAlias = srcFirst ? childRelAlias : parentRelAlias;
         String fstFld = srcFirst ? fkc.getForeignKeyFieldName() : fkc.getPrimaryKeyFieldName();

         String sndAlias = srcFirst ? parentRelAlias : childRelAlias;
         String sndFld = srcFirst ? fkc.getPrimaryKeyFieldName() : fkc.getForeignKeyFieldName();

         if ( fstAlias.length() > 0 )
         {
            sb.append(fstAlias);
            sb.append('.');
         }
         sb.append(fstFld);

         sb.append(" = ");

         if ( sndAlias.length() > 0 )
         {
            sb.append(sndAlias);
            sb.append('.');
         }
         sb.append(sndFld);
      }

      return sb.toString();
   }

   public boolean foreignKeyFieldNamesSetEquals(Set<String> normdReqdFkFieldNames)
   {
      if ( getForeignKeyComponents().size() != normdReqdFkFieldNames.size() )
         return false;

      Set<String> fkFieldNames = new HashSet<>();

      for ( ForeignKey.Component fk_comp: getForeignKeyComponents() )
         fkFieldNames.add(fk_comp.getForeignKeyFieldName());

      return fkFieldNames.equals(normdReqdFkFieldNames);
   }

   public static class Component
   {
      private final String foreignKeyFieldName;

      private final String primaryKeyFieldName;

      public Component(String fkName, String pkName)
      {
         foreignKeyFieldName = fkName;
         primaryKeyFieldName = pkName;
      }

      private Component()
      {
         this.foreignKeyFieldName = "";
         this.primaryKeyFieldName = "";
      }

      public String getForeignKeyFieldName() { return foreignKeyFieldName; }

      public String getPrimaryKeyFieldName() { return primaryKeyFieldName; }
   }
}

