#include <stdio.h>

/* Recursive version (slow for large n, fine for demonstration) */
int fib_recursive(int n) {
    if (n <= 1) return n;
    return fib_recursive(n - 1) + fib_recursive(n - 2);
}

int main() {
    int i;
    long long a = 0, b = 1;

    printf("Iterative Fibonacci:\n");
    for (i = 0; i < 15; i++) {
        printf("%lld ", a);
        long long next = a + b;
        a = b;
        b = next;
    }

    printf("\n\nRecursive Fibonacci:\n");
    for (i = 0; i < 15; i++) {
        printf("%d ", fib_recursive(i));
    }
    printf("\n");
    return 0;
}
