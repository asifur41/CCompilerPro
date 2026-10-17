#include <stdio.h>

int gcd(int a, int b) {
    return b == 0 ? a : gcd(b, a % b);
}

long power(int base, int exp) {
    if (exp == 0) return 1;
    return base * power(base, exp - 1);
}

int digit_sum(int n) {
    if (n == 0) return 0;
    return (n % 10) + digit_sum(n / 10);
}

void hanoi(int n, char from, char to, char via) {
    if (n == 0) return;
    hanoi(n - 1, from, via, to);
    printf("Move disk %d from %c to %c\n", n, from, to);
    hanoi(n - 1, via, to, from);
}

int main() {
    printf("gcd(48, 18)   = %d\n", gcd(48, 18));
    printf("power(2, 10)  = %ld\n", power(2, 10));
    printf("digit_sum(12345) = %d\n\n", digit_sum(12345));
    printf("Tower of Hanoi with 3 disks:\n");
    hanoi(3, 'A', 'C', 'B');
    return 0;
}
