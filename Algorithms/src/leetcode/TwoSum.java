package leetcode;

import java.util.Arrays;

public class TwoSum {
    public static void main(String[] args) {
        int[] numers = {3,2,4};
        System.out.println(Arrays.toString(twoSum(numers, 6)));
    }

    public static int[] twoSum(int[] nums, int target) {
        int[] num = new int[2];
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    num[0] = i;
                    num[1] = j;
                    return num;
                }
            }
        }
        return num;
    }
}
