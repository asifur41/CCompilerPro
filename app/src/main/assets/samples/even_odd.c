/*
 * Title: Even or Odd
 * Uses the remainder operator % to decide whether each number is even or odd.
 */
#include <stdio.h>

int main() {
    int n;

    for (n = 1; n <= 8; n++) {
        if (n % 2 == 0) {
            printf("%d is even\n", n);
        } else {
            printf("%d is odd\n", n);
        }
    }
    return 0;
}
