package org.sqljson.dbmd;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.checkerframework.checker.nullness.qual.Nullable;

import org.sqljson.util.StringFuns;

class ForeignKeyBuilder
{
   private String constraintName;
   private RelId srcRel;
   private RelId tgtRel;
   private List<ForeignKey.Component> comps;

   public ForeignKeyBuilder
      (
         String constraintName,
         RelId srcRel,
         RelId tgtRel
      )
   {
      this.constraintName = constraintName;
      this.srcRel = srcRel;
      this.tgtRel = tgtRel;
      this.comps = new ArrayList<>();
   }

   boolean neitherRelMatches(Pattern relIdsPattern)
   {
      return !(StringFuns.matches(relIdsPattern, srcRel.getIdString()) || StringFuns.matches(relIdsPattern, tgtRel.getIdString()));
   }

   ForeignKey build() { return new ForeignKey(constraintName, srcRel, tgtRel, comps); }

   void addComponent(ForeignKey.Component comp) { comps.add(comp); }
}

