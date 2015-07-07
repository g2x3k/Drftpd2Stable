/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.misc;

import java.util.Map;

// Test program for the LRUCache class.
public class TestLRUCache {

public static void main (String[] args) {
   LRUCache<String,String> c = new LRUCache<String,String>(3);
   c.put ("1","one");                            // 1
   c.put ("2","two");                            // 2 1
   c.put ("3","three");                          // 3 2 1
   c.put ("4","four");                           // 4 3 2
   if (c.get("2")==null) throw new Error();      // 2 4 3
   c.put ("5","five");                           // 5 2 4
   c.put ("4","second four");                    // 4 5 2
   // Verify cache content.
   if (c.usedEntries() != 3)              throw new Error();
   if (!c.get("4").equals("second four")) throw new Error();
   if (!c.get("5").equals("five"))        throw new Error();
   if (!c.get("2").equals("two"))         throw new Error();
   // List cache content.
   for (Map.Entry<String,String> e : c.getAll())
      System.out.println (e.getKey() + " : " + e.getValue()); }

} // end class TestLRUCache
