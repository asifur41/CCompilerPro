#include <stdio.h>

void swap(int *a, int *b) {
    int tmp = *a;
    *a = *b;
    *b = tmp;
}

int main() {
    int x = 10, y = 20;
    int *p = &x;
    int arr[] = {1, 2, 3, 4, 5};
    int *q = arr;
    int i;

    printf("x = %d, *p = %d\n", x, *p);
    *p = 99;
    printf("after *p = 99, x = %d\n", x);

    swap(&x, &y);
    printf("after swap: x = %d, y = %d\n", x, y);

    printf("Array via pointer arithmetic: ");
    for (i = 0; i < 5; i++) {
        printf("%d ", *(q + i));
    }
    printf("\n");
    return 0;
}
