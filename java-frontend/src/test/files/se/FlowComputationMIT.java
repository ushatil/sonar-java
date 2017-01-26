import java.util.*;

class A {

  private boolean trueIfNull(Object a) {
    if (a == null) {
      return true;
    }
    return false;
  }

  void exceptions2(Object o) {
    throwIfNull(o); // flow@ex2 {{'throwIfNull' returns non-null}} FIXME
    if (o != null) { // Noncompliant [[flows=ex2]] flow@ex2

    }
  }

  void test(Object a) {
    if (trueIfNull(a)) { // flow@arg {{'trueIfNull' returns null}} FIXME
      a.toString(); // Noncompliant [[flows=arg]] {{NullPointerException might be thrown as 'a' is nullable here}}  flow@arg {{'a' is dereferenced}}
    }
  }

  private Object throwIfNull(Object o) {
    if (o == null) throw new IllegalStateException(); // flow@ex {{Implies 'o' is null}}
    return o;
  }

  void exceptions(Object o) {
     try {
       throwIfNull(o); // flow@ex {{Exception 'IllegalStateException' thrown by 'throwIfNull'}}
     } catch (IllegalStateException ex) {
       o.toString(); // Noncompliant [[flows=ex]] {{NullPointerException might be thrown as 'o' is nullable here}} flow@ex {{'o' is dereferenced}}
     }
  }



}

