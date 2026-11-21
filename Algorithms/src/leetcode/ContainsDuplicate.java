package leetcode;

import java.util.HashSet;
import java.util.Set;

public class ContainsDuplicate {
    public static void main(String[] args) {
        int[] nums = {1, 2, 3, 4};
        System.out.println(containsDuplicate(nums));
    }

    public static boolean containsDuplicate(int[] nums) {
        Set<Integer> hs = new HashSet<>();
        for (int num : nums) {
            if (!hs.add(num)) {
                return true;
            }
        }
        return false;
    }
}
