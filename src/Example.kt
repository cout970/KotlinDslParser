fun script() {
    html {
        body {
            div {
                a("https://kotlinlang.org") {
//                    target = ATarget.blank
                    +"Main site"
                    div{}
                }
            }
        }
    }
}