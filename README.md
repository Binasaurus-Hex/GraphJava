# GraphJava

## Another simple programming language - this time in Java!?

Another iteration of my language "Graph". now a lot more flexible and fully featured than the original C++ version

Key goals :
- lightning fast compile times
- simple C like workflow, but with less awkwardness
- minimal or zero dependencies
- compile time execution (this is why I've added a bytecode intermediary)

Features : 
- bool, float, and int types
- arrays of aformentioned types
- structs
- type inference
- pointers
- ability to call out to java functions (limited to certain types)
- now compiles to a intermediate bytecode, and contains a virtual machine for running the bytecode.

# Examples

variable decleration
```go
x := 2;
y :int = 3;
happy := false;
```

vector length procedure
```go
length :: (x: float, y: float) -> float {
    <- sqrt(x * x + y * y);
}
```

arrays
```go
key_codes: [3]int;
key_codes[0] = 2;
key_codes[1] = 4;
key_codes[2] = 8;
```

structs
```go
Vector2 :: struct {
    x: float;
    y: float;
}

...

position: Vector2;
position.x = 50;

/* struct literals */
velocity: Vector2 = { 0.1 , 0.2 };
```

control flow
```go
counter := 0;
while counter < 100 {
  if counter == 50 {
      print(counter);
  }
  counter = counter + 1;
}
```

# Moving a square around the screen
```go
Vector2i :: struct {
    x: int;
    y: int;
}

Vector2 :: struct {
    x: float;
    y: float;
}

Color :: struct {
    r: float;
    g: float;
    b: float;
    a: float;
}

set_color :: (color: *Color){
    extern_set_colour(color.r, color.g, color.b, color.a);
}

get_mouse_position :: () -> Vector2i {
    <- { get_mouse_x(), get_mouse_y() };
}

main :: () {

    window_width := 1500;
    window_height := 1000;

    open_window(window_width, window_height);

    // colours
    red:   Color = { 1., 0. + 0.5, 0., 1. };
    blue:  Color = { 0., 0., 1., 1. };
    black: Color = { 0., 0., 0., 1. };

    box_size := 100;

    while true {
        frame_begin();
        set_color(&black);
        fill_rect(0, 0, window_width, window_height);

        mouse_position := get_mouse_position();

        set_color(&red);
        fill_rect(mouse_position.x, mouse_position.y, box_size, box_size);
        draw();
    }
}
```
## whats next?
I'd like to get a few more things in before transitioning the code back to C++, and writing a backend to convert the bytecode to machine code.
Hopefully this will remove the dependency on the NASM assembler I used on the previous iteration.
  
