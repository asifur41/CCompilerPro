#include <stdio.h>
#include <stdlib.h>

struct Node {
    int value;
    struct Node *next;
};

struct Node *push_front(struct Node *head, int value) {
    struct Node *node = (struct Node *)malloc(sizeof(struct Node));
    if (node == NULL) return head;
    node->value = value;
    node->next = head;
    return node;
}

void print_list(const struct Node *head) {
    while (head != NULL) {
        printf("%d -> ", head->value);
        head = head->next;
    }
    printf("NULL\n");
}

void free_list(struct Node *head) {
    while (head != NULL) {
        struct Node *next = head->next;
        free(head);
        head = next;
    }
}

int main() {
    struct Node *list = NULL;
    int i;

    for (i = 1; i <= 5; i++) {
        list = push_front(list, i * 10);
    }

    print_list(list);
    free_list(list);
    return 0;
}
