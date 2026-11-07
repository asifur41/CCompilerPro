/*
 * Title: Matrix Addition
 * Adds two 3x3 matrices element by element with two nested loops.
 */
#include <stdio.h>

#define N 3

void print_matrix(const char *name, int m[N][N]) {
    int i, j;

    printf("%s:\n", name);
    for (i = 0; i < N; i++) {
        for (j = 0; j < N; j++) {
            printf("%4d", m[i][j]);
        }
        printf("\n");
    }
}

int main() {
    int a[N][N] = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    int b[N][N] = {{9, 8, 7}, {6, 5, 4}, {3, 2, 1}};
    int sum[N][N];
    int i, j;

    for (i = 0; i < N; i++) {
        for (j = 0; j < N; j++) {
            sum[i][j] = a[i][j] + b[i][j];
        }
    }

    print_matrix("A", a);
    print_matrix("B", b);
    print_matrix("A + B", sum);
    return 0;
}
