package org.sqljson.query_specs;

import java.io.IOException;

import org.checkerframework.checker.nullness.qual.Nullable;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.node.JsonNodeType;


// Allow deserializing from simple String as "field" property.
public final class TableFieldExpr
{
   private String field;
   private String expression;
   private String withTableAliasAs;
   private String jsonProperty;
   private String fieldTypeInGeneratedSource;

   private TableFieldExpr() {}

   public TableFieldExpr
      (
         String field,
         String expression,
         String withTableAliasAs,
         String jsonProperty,
         String fieldTypeInGeneratedSource
      )
   {
      this.field = field;
      this.expression = expression;
      this.withTableAliasAs = withTableAliasAs;
      this.jsonProperty = jsonProperty;
      this.fieldTypeInGeneratedSource = fieldTypeInGeneratedSource;

      if ( (field != null) == (expression != null) )
         throw new RuntimeException("Exactly one of database field name and value expression should be specified.");
      if ( withTableAliasAs != null && expression == null )
         throw new RuntimeException("Cannot specify withTableAliasAs without expression value.");
   }

   public String getField() { return field; }

   public String getExpression() { return expression; }

   public String getWithTableAliasAs() { return withTableAliasAs; }

   public String getJsonProperty() { return jsonProperty; }

   public String getFieldTypeInGeneratedSource() { return fieldTypeInGeneratedSource; }
}

/// Allow simple String to be deserialized to a TableFieldExpression with the value as the "field"
/// property and other values null.
class TableFieldExprDeserializer extends JsonDeserializer<TableFieldExpr>
{
   @Override
   public TableFieldExpr deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException, JsonProcessingException
   {
      JsonNode node = jsonParser.readValueAs(JsonNode.class);

      if ( node.getNodeType() == JsonNodeType.STRING )
         return new TableFieldExpr(node.textValue(), null, null, null, null);
      else
      {
         String field = node.has("field") ? node.get("field").textValue(): null;
         String expr = node.has("expression") ? node.get("expression").textValue(): null;
         String withTableAliasAs = node.has("withTableAliasAs") ? node.get("withTableAliasAs").textValue(): null;
         String jsonProperty = node.has("jsonProperty") ? node.get("jsonProperty").textValue(): null;
         String genFieldType = node.has("fieldTypeInGeneratedSource") ? node.get("fieldTypeInGeneratedSource").textValue(): null;
         return new TableFieldExpr(field, expr, withTableAliasAs, jsonProperty, genFieldType);
      }
   }
}
