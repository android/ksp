// TEST PROCESSOR: ParentProcessor
// EXPECTED:
// parent of File: a.kt: null
// parent of Anno: File: a.kt
// parent of Anno: synthetic constructor for Anno
// parent of synthetic constructor for Anno: Anno
// parent of Int: Int
// parent of Int: INVARIANT Int
// parent of INVARIANT Int: Map
// parent of T: T
// parent of T: INVARIANT T
// parent of INVARIANT T: Map
// parent of Map: Map
// parent of Map: Alias
// parent of T: Alias
// parent of Alias: File: a.kt
// parent of Int: Int
// parent of Int: INVARIANT Int
// parent of INVARIANT Int: List
// parent of List: List
// parent of List: topProp
// parent of Int: Int
// parent of Int: INVARIANT Int
// parent of INVARIANT Int: List<Int>
// parent of List<Int>: List<Int>
// parent of List<Int>: topProp.getter()
// parent of topProp.getter(): topProp
// parent of Anno: Anno
// parent of Anno: @Anno
// parent of @Anno: topProp
// parent of topProp: File: a.kt
// parent of T: T
// parent of T: topFun
// parent of T: topFun
// parent of Anno: Anno
// parent of Anno: @Anno
// parent of @Anno: topFun
// parent of topFun: File: a.kt
// parent of ITF: File: a.kt
// parent of ITF: ITF
// parent of ITF: topClass
// parent of Anno: Anno
// parent of Anno: @Anno
// parent of @Anno: topClass
// parent of topClass: File: a.kt
// parent of Int: Int
// parent of Int: i
// parent of i: memberFun
// parent of Int: memberFun
// parent of memberFun: topClass
// parent of P: InnerClass
// parent of InnerClass: topClass
// parent of P: P
// parent of P: p
// parent of p: innerFun
// parent of Int: innerFun
// parent of innerFun: InnerClass
// parent of InnerClass<*>: synthetic constructor for InnerClass
// parent of synthetic constructor for InnerClass: InnerClass
// parent of Int: Int
// parent of Int: a
// parent of Int: Int
// parent of Int: a.getter()
// parent of a.getter(): a
// parent of a: topClass
// parent of String: String
// parent of String: b
// parent of b.getter(): b
// parent of String: String
// parent of String: <set-?>
// parent of <set-?>: b.setter()
// parent of b.setter(): b
// parent of b: topClass
// parent of topClass: synthetic constructor for topClass
// parent of synthetic constructor for topClass: topClass
// parent of File: B.java: null
// parent of Object: Object
// parent of Object: B
// parent of ITF: ITF
// parent of ITF: B
// parent of T: B
// parent of Anno: @Anno
// parent of @Anno: B
// parent of B: File: B.java
// parent of T: T
// parent of T: t
// parent of t: B
// parent of T: T
// parent of T: t
// parent of t: foo
// parent of Int: Int
// parent of Int: i
// parent of i: foo
// parent of Int: Int
// parent of Int: foo
// parent of foo: B
// parent of B<*>: synthetic constructor for B
// parent of synthetic constructor for B: B
// END

// FILE: a.kt
annotation class Anno

typealias Alias<T> = Map<Int, T>

@Anno
val topProp : List<Int>? = null

@Anno
fun <T> topFun() : T? {
    return null
}

interface ITF

@Anno
class topClass: ITF {
    fun memberFun(i: Int) = 1
    class InnerClass<P> {
        fun innerFun(p: P) = 1
    }

    val a: Int = 1
    var b: String
    get() = "1"
}

// FILE: B.java
@Anno
public class B<T> implements ITF {
    private T t;
    public int foo(T t, int i) {
        return 1;
    }
}
