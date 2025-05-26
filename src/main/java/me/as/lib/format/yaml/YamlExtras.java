/*
 * Copyright 2019 Antonio Sorrentini
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package me.as.lib.format.yaml;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.as.lib.format.json.JsonExtras;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.util.Map;
import java.io.File;
import java.util.HashSet;
import java.util.Set;


import static me.as.lib.format.json.JsonExtras.KeyConflictPolicy;
import static me.as.lib.format.json.JsonExtras.ArrayKeyConflictPolicy;
import static me.as.lib.core.lang.ExceptionExtras.showErrorAtLine;
import static me.as.lib.core.log.LogEngine.logOut;
import static me.as.lib.core.system.FileSystemExtras.loadTextFromFile;
import static me.as.lib.format.json.JsonExtras.quickJson;


public class YamlExtras
{
 // singleton
 private YamlExtras(){}

 // . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .


 public static JsonObject loadJsonFromYAMLFile(String fname)
 {
  return loadJsonFromYAMLFile(JsonObject.class, fname);
 }


 public static <T> T loadJsonFromYAMLFile(Class<T> clazz, String fname)
 {
  try
  {
   return fromYAMLString(clazz, loadTextFromFile(fname));
  }
  catch (Throwable tr)
  {
   return null;
  }
 }


 public static JsonObject fromYAMLString(String yaml)
 {
  return fromYAMLString(JsonObject.class, yaml);
 }

 public static <T> T fromYAMLString(Class<T> clazz, String yaml)
 {
  T res;

  try
  {
   LoadSettings settings = LoadSettings.builder().build();
   Load load = new Load(settings);
   Map<String, Object> data = (Map<String, Object>) load.loadFromString(yaml);

   JsonObject jres=quickJson(data);

   if (clazz==jres.getClass())
    res=(T)jres;
   else
    res=JsonExtras.fromString(clazz, JsonExtras.toString(jres));
  }
  catch (Throwable tr)
  {
   showErrorAtLine(tr.getMessage(), "OFFENDING JSON", yaml);
   throw new RuntimeException(tr);
  }

  return res;
 }



 /**
  * Loads a YAML file with support for recursive includes
  *
  * @param path Path to the main YAML file
  * @return JsonObject with all includes processed and merged
  * @throws RuntimeException if there's a circular include or I/O errors
  */
 public static JsonObject loadYamlWithIncludes(String path)
 {
  return loadYamlWithIncludes(path, new HashSet<>());
 }

 /**
  * Internal recursive method to load YAML with includes
  *
  * @param path Path to the YAML file to load
  * @param visited Set of already visited paths to avoid cycles
  * @return JsonObject resulting from merging all includes
  */
 private static JsonObject loadYamlWithIncludes(String path, Set<String> visited)
 {
  try
  {
   // Get absolute path
   File file = new File(path);
   String absolutePath = file.getAbsolutePath();

   // Check for circular includes
   if (visited.contains(absolutePath))
   {
    throw new RuntimeException("Circular include detected for " + absolutePath);
   }
   visited.add(absolutePath);

   // Load current YAML file
   JsonObject current = loadJsonFromYAMLFile(absolutePath);
   if (current == null)
   {
    current = new JsonObject();
   }

   // Initialize merged result
   JsonObject merged = new JsonObject();

   // Get base directory for relative paths
   String baseDir = file.getParent();
   if (baseDir == null) baseDir = ".";

   // Extract includes list
   JsonArray includes = null;

   if (current.has("includes"))
   {
    try
    {
     // Try to get as string array
     includes = JsonExtras.cast(current.get("includes"), JsonArray.class);
     // Remove includes from current to avoid including it in final merge
     current.remove("includes");
    }
    catch (Throwable tr)
    {
     // If it fails, ignore silently
     logOut.println("Warning: could not parse 'includes' field in " + path);
    }
   }

   // Process each include
   if (includes != null)
   {
    for (JsonElement include : includes.asList())
    {
     // Build absolute path for the include
     File includeFile = new File(baseDir, include.getAsString());
     String includePath = includeFile.getAbsolutePath();

     // Recursively load the include
     JsonObject includedYaml = loadYamlWithIncludes(includePath, new HashSet<>(visited));

     // Merge the include into result
     merged = JsonExtras.mergeJsons(
      merged,
      includedYaml,
      KeyConflictPolicy.VALUE_FROM_SECOND,  // Include overwrites
      true,  // Recursive merge of objects
      ArrayKeyConflictPolicy.APPEND_SECOND_TO_FIRST  // Append arrays
     );
    }
   }

   // Final merge with current file (takes precedence)
   merged = JsonExtras.mergeJsons(
    merged,
    current,
    KeyConflictPolicy.VALUE_FROM_SECOND,  // Current file overwrites
    true,  // Recursive merge of objects
    ArrayKeyConflictPolicy.APPEND_SECOND_TO_FIRST  // Append arrays
   );

   // Remove current path from visited to allow reuse in other branches
   visited.remove(absolutePath);

   return merged;
  }
  catch (Throwable tr)
  {
   throw new RuntimeException("Error processing YAML file " + path + ": " + tr.getMessage(), tr);
  }
 }


}
