#include <stdio.h>

unsigned long long factorial(int n) {
    unsigned long long result = 1;
    int i;
    for (i = 2; i <= n; i++) {
        result *= i;
    }
    return result;
}

int main() {
    int n;
    printf("Factorials (20! is the largest that fits in 64 bits)\n");
    for (n = 0; n <= 20; n++) {
        printf("%2d! = %llu\n", n, factorial(n));
    }
    return 0;
}
