import java.util.*;

class A {

  private boolean trueIfNull(Object a) {
    if (a == null) { // flow@arg,nested {{Implies 'a' is null}}
      return true;
    }
    return false;
  }

  private Object throwIfNull(Object o) {
    if (o == null) throw new IllegalStateException(); // flow@ex {{Implies 'o' is null}}  flow@ex2 {{Implies 'o' is non-null}}
    return o;
  }

  void exceptions2(Object o) {
    throwIfNull(o); // flow@ex2  {{Implies arg #1 'o' is non-null}} flow@ex2 {{'throwIfNull' returns non-null}}
    if (o != null) { // Noncompliant [[flows=ex2]] {{Change this condition so that it does not always evaluate to "true"}} flow@ex2 {{Condition is always true}}

    }
  }

  void test(Object a) {
    if (trueIfNull(a)) { // flow@arg {{Implies arg #1 'a' is null}}
      a.toString(); // Noncompliant [[flows=arg]] {{NullPointerException might be thrown as 'a' is nullable here}}  flow@arg {{'a' is dereferenced}}
    }
  }

  void exceptions(Object o) {
     try {
       throwIfNull(o); // flow@ex {{Implies arg #1 'o' is null}} flow@ex {{'IllegalStateException' thrown by 'throwIfNull'}}
     } catch (IllegalStateException ex) {
       o.toString(); // Noncompliant [[flows=ex]] {{NullPointerException might be thrown as 'o' is nullable here}} flow@ex {{'o' is dereferenced}}
     }
  }

  private boolean callTrueIfNull(Object a) {
    return trueIfNull(a); // flow@nested
  }

  void nestedTest(Object a) {
    if (callTrueIfNull(a)) { // flow@nested
      a.toString(); // Noncompliant [[flows=nested]] flow@nested
    }
  }

}

