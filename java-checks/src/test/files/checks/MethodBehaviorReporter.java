class A {
  void foo() { // Noncompliant
  }

  Object bar(int i) { // Noncompliant
    return null;
  }

  Object qix() { // Noncompliant
    System.out.println("yolo"); // Noncompliant
    return bar(0); // Noncompliant
  }

}