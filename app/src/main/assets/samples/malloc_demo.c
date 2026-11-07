/*
 * Title: Dynamic Memory with malloc()
 * Asks for memory while the program runs, fills it, prints it and gives it back with free().
 */
#include <stdio.h>
#include <stdlib.h>

int main() {
    int n = 5;
    int *squares = (int *)malloc(n * sizeof(int));
    int sum = 0;
    int i;

    if (squares == NULL) {
        printf("Out of memory\n");
        return 1;
    }

    for (i = 0; i < n; i++) {
        squares[i] = (i + 1) * (i + 1);
        sum += squares[i];
    }

    printf("Squares: ");
    for (i = 0; i < n; i++) {
        printf("%d ", squares[i]);
    }
    printf("\nSum: %d\n", sum);

    free(squares);
    return 0;
}
