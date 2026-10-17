#include <stdio.h>
#include <string.h>

struct Student {
    char name[32];
    int age;
    float gpa;
};

void print_student(const struct Student *s) {
    printf("%-10s age %d  GPA %.2f\n", s->name, s->age, s->gpa);
}

int main() {
    struct Student students[3];
    int i;
    float total = 0.0f;

    strcpy(students[0].name, "Alice");
    students[0].age = 20;
    students[0].gpa = 3.8f;

    strcpy(students[1].name, "Bob");
    students[1].age = 22;
    students[1].gpa = 3.2f;

    strcpy(students[2].name, "Chitra");
    students[2].age = 21;
    students[2].gpa = 3.9f;

    for (i = 0; i < 3; i++) {
        print_student(&students[i]);
        total += students[i].gpa;
    }

    printf("Average GPA: %.2f\n", total / 3);
    return 0;
}
