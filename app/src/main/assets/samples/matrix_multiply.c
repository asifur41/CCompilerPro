#include <stdio.h>

#define N 3

void multiply(int a[N][N], int b[N][N], int result[N][N]) {
    int i, j, k;
    for (i = 0; i < N; i++) {
        for (j = 0; j < N; j++) {
            result[i][j] = 0;
            for (k = 0; k < N; k++) {
                result[i][j] += a[i][k] * b[k][j];
            }
        }
    }
}

void print_matrix(const char *name, int m[N][N]) {
    int i, j;
    printf("%s:\n", name);
    for (i = 0; i < N; i++) {
        for (j = 0; j < N; j++) printf("%5d", m[i][j]);
        printf("\n");
    }
}

int main() {
    int a[N][N] = {{1, 2, 3}, {4, 5, 6}, {7, 8, 9}};
    int b[N][N] = {{9, 8, 7}, {6, 5, 4}, {3, 2, 1}};
    int c[N][N];

    multiply(a, b, c);
    print_matrix("A", a);
    print_matrix("B", b);
    print_matrix("A x B", c);
    return 0;
}
