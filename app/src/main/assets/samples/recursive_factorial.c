/*
 * Title: Recursive Factorial
 * A function that calls itself: n! = n x (n - 1)!, stopping at 0! = 1.
 */
#include <stdio.h>

unsigned long long factorial(int n) {
    if (n <= 1) {
        return 1;
    }
    return n * factorial(n - 1);
}

int main() {
    int n;

    for (n = 0; n <= 10; n++) {
        printf("%2d! = %llu\n", n, factorial(n));
    }
    return 0;
}
