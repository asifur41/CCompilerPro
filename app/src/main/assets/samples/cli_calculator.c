/*
 * Title: Command-Line Calculator
 * Reads number, operator, number from argc/argv, for example: 12.5 * 4. Turn on Settings > Editor > Program arguments, type the values next to Run, then tap Run.
 */
#include <stdio.h>
#include <stdlib.h>

int main(int argc, char *argv[]) {
    char *end;
    double a, b, result;
    char op;

    if (argc != 4) {
        printf("Usage: %s number operator number\n", argv[0]);
        printf("Example: 12.5 * 4\n");
        printf("Operators: +  -  *  x  /\n");
        return 1;
    }

    a = strtod(argv[1], &end);
    if (end == argv[1] || *end != '\0') {
        printf("Error: '%s' is not a number\n", argv[1]);
        return 1;
    }
    b = strtod(argv[3], &end);
    if (end == argv[3] || *end != '\0') {
        printf("Error: '%s' is not a number\n", argv[3]);
        return 1;
    }
    if (argv[2][0] == '\0' || argv[2][1] != '\0') {
        printf("Error: unknown operator '%s'\n", argv[2]);
        return 1;
    }
    op = argv[2][0];

    switch (op) {
        case '+': result = a + b; break;
        case '-': result = a - b; break;
        case '*':
        case 'x': result = a * b; break;
        case '/':
            if (b == 0) {
                printf("Error: division by zero\n");
                return 1;
            }
            result = a / b;
            break;
        default:
            printf("Error: unknown operator '%s'\n", argv[2]);
            return 1;
    }

    printf("%g %c %g = %g\n", a, op, b, result);
    return 0;
}
