#include <stdio.h>
#include <stdbool.h>

bool is_prime(int n) {
    int i;
    if (n < 2) return false;
    if (n % 2 == 0) return n == 2;
    for (i = 3; i * i <= n; i += 2) {
        if (n % i == 0) return false;
    }
    return true;
}

int main() {
    int tests[] = {1, 2, 17, 21, 97, 100, 7919};
    int count = sizeof(tests) / sizeof(tests[0]);
    int i;

    for (i = 0; i < count; i++) {
        printf("%d is %s\n", tests[i], is_prime(tests[i]) ? "prime" : "not prime");
    }

    printf("\nPrimes below 60:\n");
    for (i = 2; i < 60; i++) {
        if (is_prime(i)) printf("%d ", i);
    }
    printf("\n");
    return 0;
}
