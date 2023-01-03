package org.sqljson.dbmd;

import static java.util.Objects.requireNonNull;

import org.checkerframework.checker.nullness.qual.Nullable;

public class RelDescr {

   private RelId relId;

   private RelMetadata.RelType relType;

   private String relComment;

   public RelDescr
      (
         RelId relId,
         RelMetadata.RelType relType,
         String relComment
      )
   {
      this.relId = requireNonNull(relId);
      this.relType = requireNonNull(relType);
      this.relComment = relComment;
   }

   private RelDescr()
   {
      this.relId = RelId.DUMMY_INSTANCE;
      this.relType = RelMetadata.RelType.Table;
   }

   public RelId getRelationId() { return relId; }

   public RelMetadata.RelType getRelationType() { return relType; }

   public String getRelationComment() { return relComment; }
}
