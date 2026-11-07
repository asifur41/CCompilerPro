/*
 * Title: Reverse a Number
 * Takes the last digit of a number again and again to build the reversed number.
 */
#include <stdio.h>

int reverse_number(int n) {
    int reversed = 0;

    while (n > 0) {
        reversed = reversed * 10 + n % 10;
        n = n / 10;
    }
    return reversed;
}

int main() {
    printf("12345 reversed is %d\n", reverse_number(12345));
    printf("9080 reversed is %d\n", reverse_number(9080));
    return 0;
}
