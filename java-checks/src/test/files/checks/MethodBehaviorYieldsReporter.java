class A {
  void foo() { // Noncompliant
  }

  Object bar(int i) { // Noncompliant
    return null;
  }

  Object qix() { // Noncompliant

    System.out.println("yolo"); // Compliant
    return bar(0); // Compliant
  }

  Object gul() { // Noncompliant
    if (42 > 15) {
      return null;
    }
    return new Object();
  }

}
