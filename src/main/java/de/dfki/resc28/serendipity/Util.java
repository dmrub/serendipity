/*
 * This file is part of serendipity. It is subject to the license terms in
 * the LICENSE file found in the top-level directory of this distribution.
 * You may not use this file except in compliance with the License.
 */

package de.dfki.resc28.serendipity;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

/**
*
* @author Dmitri Rubinstein
*/
public final class Util 
{
   public static String urlEncoded(String text) 
   {
       try 
       {
           return URLEncoder.encode(text, "UTF-8");
       } 
       catch (UnsupportedEncodingException ex) 
       {
           System.err.format("Could not convert string to UTF-8: %s%n", ex);
           return text;
       }
   }

   public static String appendSegmentToPath(String path, String segment) 
   {
       boolean segmentStartsWithSlash = !segment.isEmpty() && segment.charAt(0) == '/';

       if (path == null || path.isEmpty()) 
       {
           return segmentStartsWithSlash ? segment : "/" + segment;
       }

       if (path.charAt(path.length() - 1) == '/') 
       {
           return segmentStartsWithSlash ? path + segment.substring(1) : path + segment;
       }

       return segmentStartsWithSlash ? path + segment : path + "/" + segment;
   }

   public static String joinPath(String... args) 
   {
       if (args.length == 0)
           return "";
       String path = args[0];
       for (int i = 1; i < args.length; ++i) 
       {
           path = appendSegmentToPath(path, args[i]);
       }
       return path;
   }

}