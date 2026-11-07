/*
 * Title: Largest of Three Numbers
 * Compares three numbers with if / else if and prints the biggest one.
 */
#include <stdio.h>

int largest(int a, int b, int c) {
    if (a >= b && a >= c) {
        return a;
    } else if (b >= a && b >= c) {
        return b;
    }
    return c;
}

int main() {
    printf("Largest of 12, 45, 30 is %d\n", largest(12, 45, 30));
    printf("Largest of 90, 7, 15 is %d\n", largest(90, 7, 15));
    printf("Largest of -4, -9, -1 is %d\n", largest(-4, -9, -1));
    return 0;
}
