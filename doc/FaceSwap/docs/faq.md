### 1. I'm getting "Illegal instruction (core dumped)" in my log file

Please use a machine with an AVX instruction enabled CPU. To check if AVX instruction is available on your machine, use

           grep avx /proc/cpuinfo
