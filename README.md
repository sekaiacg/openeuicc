9eSIM LPA
---

This is a soft fork and branded version of [OpenEUICC](https://gitea.angry.im/PeterCxy/OpenEUICC) for the purpose of the [9eSIM eSIM Card](https://www.9esim.com).

[lpac](https://github.com/estkme-group/lpac) is available for use on a Linux/Windows/macOS PC.

A fully free and open-source Local Profile Assistant implementation for Android devices.

There are two variants of this project, OpenEUICC and EasyEUICC:

|                               |                    OpenEUICC                    |     EasyEUICC     |
|:------------------------------|:-----------------------------------------------:|:-----------------:|
| Privileged                    |         Must be installed as system app         |        No         |
| Internal eSIM                 |                    Supported                    |    Unsupported    |
| External (Removable) eSIM     |                    Supported                    |     Supported     |
| USB Readers                   |                    Supported                    |     Supported     |
| Requires allowlisting by eSIM |                       No                        | Yes -- except USB |
| System Integration            | Partial (carrier partner API unimplemented yet) |        No         |

Some side notes:
1. When privileged, OpenEUICC supports any eUICC chip that implements the SGP.22 standard, internal or external. However, there is __no guarantee__ that external (removable) eSIMs actually follow the standard. Please __DO NOT__ submit bug reports for non-functioning removable eSIMs. They are __NOT__ officially supported unless they also support / are supported by EasyEUICC, the unprivileged variant.
2. Both variants support accessing eUICC chips through USB CCID readers, regardless of whether the chip contains the correct ARA-M hash to allow for unprivileged access. However, only `T=0` readers that use the standard [USB CCID protocol](https://en.wikipedia.org/wiki/CCID_(protocol)) are supported.
3. Prebuilt release-mode EasyEUICC apks can be downloaded [here](https://gitea.angry.im/PeterCxy/OpenEUICC/releases). For OpenEUICC, no official release is currently provided and only debug mode APKs can be found in the CI page.
4. For removable eSIM chip vendors: to have your chip supported by official builds of EasyEUICC when inserted, include the ARA-M hash `2A2FA878BC7C3354C2CF82935A5945A3EDAE4AFA`.

__This project is Free Software licensed under GNU GPL v3, WITHOUT the "or later" clause.__ Any modification and derivative work __MUST__ be released under the SAME license, which means, at the very least, that the source code __MUST__ be available upon request.

__If you are releasing a modification of this app, you are kindly asked to make changes to at least the app name and package name.__  

Copyright
===

Everything except `libs/lpac-jni` and `art/`:

```
Copyright 2022-2024 OpenEUICC contributors

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation, version 3.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
```

`libs/lpac-jni`:

```
Copyright (C) 2022-2024 OpenEUICC contributiors

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation, version 2.1.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
```

`art/`: Courtesy of [Aikoyori](https://github.com/Aikoyori), CC NC-SA 4.0.
