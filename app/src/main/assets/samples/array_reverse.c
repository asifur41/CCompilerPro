/*
 * Title: Reverse an Array
 * Swaps the first and last elements, then the second and second-to-last, until the middle.
 */
#include <stdio.h>

void print_array(const char *label, int arr[], int n) {
    int i;

    printf("%s", label);
    for (i = 0; i < n; i++) {
        printf("%d ", arr[i]);
    }
    printf("\n");
}

int main() {
    int data[] = {10, 20, 30, 40, 50, 60, 70, 80};
    int n = sizeof(data) / sizeof(data[0]);
    int i;

    print_array("Before: ", data, n);

    for (i = 0; i < n / 2; i++) {
        int temp = data[i];
        data[i] = data[n - 1 - i];
        data[n - 1 - i] = temp;
    }

    print_array("After:  ", data, n);
    return 0;
}
