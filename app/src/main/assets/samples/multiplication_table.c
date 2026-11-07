/*
 * Title: Multiplication Table
 * Prints the table of 7 from 1 to 10 with a for loop.
 */
#include <stdio.h>

int main() {
    int number = 7;
    int i;

    for (i = 1; i <= 10; i++) {
        printf("%d x %2d = %3d\n", number, i, number * i);
    }
    return 0;
}
