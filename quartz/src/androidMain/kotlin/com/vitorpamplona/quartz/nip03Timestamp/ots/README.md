This code came from https://github.com/opentimestamps/java-opentimestamps

And includes modifications to
1 - Avoid dependencies that do not work on Android
2 - Move from org.json to jackson
3 - Move from basic Url connection to OkHttp (and obey Tor settings)
4 - Generalize the use of Blockstream as a Bitcoin Block explorer.

—

Original License

The OpenTimestamps Client is free software: you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public License as published
by the Free Software Foundation, either version 3 of the License, or (at your
option) any later version.

The OpenTimestamps Client is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
below for more details.