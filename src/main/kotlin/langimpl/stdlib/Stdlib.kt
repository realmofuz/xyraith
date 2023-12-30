package stdlib

//Automatically generated in `build.gradle.kts`

val stdlibFiles = mutableMapOf<String, String>(
"std/console" to """namespace std.console {
    command log output: string -> void {
        let console: java.io.PrintStream = (java.lang.System.out<java.io.PrintStream>)
        console->println output
    }

    command log output: number -> void {
        let console: java.io.PrintStream = (java.lang.System.out<java.io.PrintStream>)
        console->println output
    }
}

"""
,"std/jdk" to """class xyrreserved.JDKHolder {}

@native
class java.io.OutputStream {}

@native
class java.io.FilterOutputStream : java.io.OutputStream {}

@native
class java.io.PrintStream : java.io.FilterOutputStream {
    @native
    command println x: number -> void {}

    @native
    command println x: string -> void {}
}

@native
class java.lang.System {
    @native
    @static
    let out: java.io.PrintStream = 0
}



"""
,"std/std" to """include "std/jdk"




"""
,"std/string" to """class std.stringbuilder {
    let jvmBuilder: java.lang.StringBuilder = (new java.lang.StringBuilder)

    command add n:number -> void {
        jvmBuilder->(java.lang.StringBuilder)append n
    }

    command add n:string -> void {
        jvmBuilder->(java.lang.StringBuilder)append n
    }

    command add n:boolean -> void {
        jvmBuilder->(java.lang.StringBuilder)append n
    }

    command toString -> string {
        return (jvmBuilder->(string)toString)
    }
}


"""
,"std/targets" to """class target {
    command sendMessage message:string -> void {}
    command giveItems id:string amount:number -> void {}
}"""
,
)