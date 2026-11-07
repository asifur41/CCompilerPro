/*
 * Title: Celsius to Fahrenheit
 * Converts temperatures with the formula F = C x 9 / 5 + 32 and prints a small table.
 */
#include <stdio.h>

int main() {
    int celsius;

    printf("Celsius   Fahrenheit\n");
    for (celsius = 0; celsius <= 100; celsius += 20) {
        double fahrenheit = celsius * 9.0 / 5.0 + 32.0;
        printf("%7d   %10.1f\n", celsius, fahrenheit);
    }
    return 0;
}
