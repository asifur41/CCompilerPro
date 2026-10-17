#include <stdio.h>
#include <stdbool.h>

void bubble_sort(int arr[], int n) {
    int i, j;
    for (i = 0; i < n - 1; i++) {
        bool swapped = false;
        for (j = 0; j < n - i - 1; j++) {
            if (arr[j] > arr[j + 1]) {
                int tmp = arr[j];
                arr[j] = arr[j + 1];
                arr[j + 1] = tmp;
                swapped = true;
            }
        }
        if (!swapped) break;
    }
}

int main() {
    int arr[] = {64, 34, 25, 12, 22, 11, 90};
    int n = sizeof(arr) / sizeof(arr[0]);
    int i;

    printf("Unsorted: ");
    for (i = 0; i < n; i++) printf("%d ", arr[i]);

    bubble_sort(arr, n);

    printf("\nSorted:   ");
    for (i = 0; i < n; i++) printf("%d ", arr[i]);
    printf("\n");
    return 0;
}
