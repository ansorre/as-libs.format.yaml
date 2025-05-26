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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;


import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.Map;

import static me.as.lib.format.yaml.YamlExtras.loadYamlWithIncludes;
import static me.as.lib.format.json.JsonExtras.loadJsonFromFile;
import static me.as.lib.format.yaml.YamlExtras.fromYAMLString;
import static org.junit.jupiter.api.Assertions.*;


public class YamlExtrasTest
{

 @Test
 @DisplayName("Should parse YAML string correctly")
 public void testFromYAMLString()
 {
  // Given
  String yamlInput="""
   key: Hello, world!
   pippo:
     - ciro: Ehi 1
     - pappe: Ehi 2
     - pippo: Ehi 3
   """;

  // When
  JsonObject result=fromYAMLString(yamlInput);

  // Then
  assertNotNull(result, "Result should not be null");
  assertTrue(result.has("key"), "Should contain 'key' property");
  assertTrue(result.has("pippo"), "Should contain 'pippo' property");

  assertEquals("Hello, world!", result.get("key").getAsString());
  assertTrue(result.get("pippo").isJsonArray(), "pippo should be an array");

  // Debug output
  System.out.println("Parsed YAML: "+result);
 }


 @Test
 @DisplayName("Should load YAML with includes and match expected result")
 public void testLoadYamlWithIncludes()
 {
  Path tempDir=null;

  try
  {
   // 1) Create temporary directory in user home
   String userHome=System.getProperty("user.home");
   tempDir=Files.createTempDirectory(Path.of(userHome), "yaml_test_");

   // List of files to copy from resources
   String[] resourceFiles={
    "basic_project_setup.yaml",
    "frontend.yaml",
    "frontend_gemini.yaml",
    "gemini-2.5-pro-exp-03-25.yaml",
    "success-result.json"
   };

   // Copy all files from resources to temp directory
   for (String fileName : resourceFiles)
   {
    copyResourceToTemp(fileName, tempDir);
   }

   // 2) Execute the test
   String frontendGeminiPath=tempDir.resolve("frontend_gemini.yaml").toString();
   String successResultPath=tempDir.resolve("success-result.json").toString();

   JsonObject jobj=loadYamlWithIncludes(frontendGeminiPath);
   JsonObject jok=loadJsonFromFile(successResultPath);

   // 3) Verify that jobj and jok are equal
   assertNotNull(jobj, "YAML result should not be null");
   assertNotNull(jok, "JSON reference should not be null");

   boolean areEqual=deepEquals(jobj, jok);

   if (!areEqual)
   {
    // Debug output if they don't match
    System.out.println("=== YAML RESULT ===");
    System.out.println(jobj.toString());
    System.out.println("=== EXPECTED JSON ===");
    System.out.println(jok.toString());
   }

   assertTrue(areEqual, "YAML result should match expected JSON result");

   System.out.println("✅ YAML includes test passed successfully!");
  }
  catch (Exception e)
  {
   fail("Test failed with exception: "+e.getMessage());
  }
  finally
  {
   // 4) Clean up - delete all created files and temp directory
   if (tempDir!=null)
   {
    cleanupTempDirectory(tempDir);
   }
  }
 }

 /**
  * Copy a resource file to temporary directory
  */
 private void copyResourceToTemp(String resourceName, Path tempDir) throws IOException
 {
  try (InputStream resourceStream=getClass().getClassLoader().getResourceAsStream(resourceName))
  {
   if (resourceStream==null)
   {
    throw new IOException("Resource not found: "+resourceName);
   }

   Path targetPath=tempDir.resolve(resourceName);
   Files.copy(resourceStream, targetPath, StandardCopyOption.REPLACE_EXISTING);

   System.out.println("Copied "+resourceName+" to "+targetPath);
  }
 }

 /**
  * Recursively delete temporary directory and all its contents
  */
 private void cleanupTempDirectory(Path tempDir)
 {
  try
  {
   Files.walk(tempDir)
    .sorted((a, b) -> b.compareTo(a)) // Delete files before directories
    .forEach(path -> {
     try
     {
      Files.deleteIfExists(path);
      System.out.println("Deleted: "+path);
     }
     catch (IOException e)
     {
      System.err.println("Failed to delete: "+path+" - "+e.getMessage());
     }
    });
  }
  catch (IOException e)
  {
   System.err.println("Failed to cleanup temp directory: "+e.getMessage());
  }
 }

 /**
  * Deep comparison of two JsonObjects to check if they contain the same keys/values
  */
 private boolean deepEquals(JsonObject obj1, JsonObject obj2)
 {
  if (obj1==obj2) return true;
  if (obj1==null || obj2==null) return false;

  // Check if they have the same keys
  Set<String> keys1=obj1.keySet();
  Set<String> keys2=obj2.keySet();

  if (!keys1.equals(keys2))
  {
   System.out.println("Key sets differ:");
   System.out.println("obj1 keys: "+keys1);
   System.out.println("obj2 keys: "+keys2);
   return false;
  }

  // Check each key-value pair recursively
  for (String key : keys1)
  {
   JsonElement elem1=obj1.get(key);
   JsonElement elem2=obj2.get(key);

   if (!deepEqualsElement(elem1, elem2))
   {
    System.out.println("Value differs for key '"+key+"':");
    System.out.println("obj1["+key+"] = "+elem1);
    System.out.println("obj2["+key+"] = "+elem2);
    return false;
   }
  }

  return true;
 }

 /**
  * Deep comparison of JsonElements (handles objects, arrays, primitives, null)
  */
 private boolean deepEqualsElement(JsonElement elem1, JsonElement elem2)
 {
  if (elem1==elem2) return true;
  if (elem1==null || elem2==null) return false;

  // Both null
  if (elem1.isJsonNull() && elem2.isJsonNull()) return true;
  if (elem1.isJsonNull() || elem2.isJsonNull()) return false;

  // Both primitives
  if (elem1.isJsonPrimitive() && elem2.isJsonPrimitive())
  {
   return elem1.getAsJsonPrimitive().equals(elem2.getAsJsonPrimitive());
  }

  // Both objects
  if (elem1.isJsonObject() && elem2.isJsonObject())
  {
   return deepEquals(elem1.getAsJsonObject(), elem2.getAsJsonObject());
  }

  // Both arrays
  if (elem1.isJsonArray() && elem2.isJsonArray())
  {
   JsonArray arr1=elem1.getAsJsonArray();
   JsonArray arr2=elem2.getAsJsonArray();

   if (arr1.size()!=arr2.size()) return false;

   for (int i=0;i<arr1.size();i++)
   {
    if (!deepEqualsElement(arr1.get(i), arr2.get(i)))
    {
     return false;
    }
   }
   return true;
  }

  // Different types
  return false;
 }

}