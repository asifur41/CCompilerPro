#include <stdio.h>
#include <stdlib.h>

int compare_ints(const void *a, const void *b) {
    int x = *(const int *)a;
    int y = *(const int *)b;
    return (x > y) - (x < y);
}

void print_array(const char *label, int arr[], int n) {
    int i;
    printf("%s", label);
    for (i = 0; i < n; i++) printf("%d ", arr[i]);
    printf("\n");
}

int main() {
    int data[] = {42, 7, 19, 3, 88, 21, 5, 63};
    int n = sizeof(data) / sizeof(data[0]);

    print_array("Before: ", data, n);
    qsort(data, n, sizeof(int), compare_ints);
    print_array("After:  ", data, n);
    return 0;
}
