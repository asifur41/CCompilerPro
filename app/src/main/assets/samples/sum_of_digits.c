/*
 * Title: Sum of Digits
 * Adds up the digits of a number using the % and / operators in a loop.
 */
#include <stdio.h>

int sum_of_digits(int n) {
    int sum = 0;

    while (n > 0) {
        sum += n % 10;
        n /= 10;
    }
    return sum;
}

int main() {
    printf("Sum of digits of 9875 is %d\n", sum_of_digits(9875));
    printf("Sum of digits of 1000 is %d\n", sum_of_digits(1000));
    return 0;
}
