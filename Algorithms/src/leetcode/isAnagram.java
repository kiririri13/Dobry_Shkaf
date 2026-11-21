package leetcode;

import com.sun.source.tree.BreakTree;

import java.util.HashMap;

public class isAnagram {
    public static void main(String[] args) {
        System.out.println(isAnagram("aag", "aaf"));
    }

//    Input: s = "anagram", t = "nagaram"
//
//    Output: true

    public static boolean isAnagram(String s, String t) {
        int[] count = new int[26];
        if (s.length() != t.length()) {
            return false;
        }
        for (int i = 0; i< s.length(); i++) {
            count[s.charAt(i) - 'a']++;
            count[t.charAt(i) - 'a']--;
        }

        for (int i = 0; i < 26; i++) {
            if (count[i] != 0) {
                return false;
            }
        }
        return true;
    }
}
