###smooth_frag_shader性能：
1. 先脸部识别通过后再采样，虽然逻辑看是减少了运算量，但是实际上没有比 全部都采样但是只对脸部识别的点进行
实际改变 有更快的性能，经过mx5的测试，两种情况下MX5下的ReadPixel的时间都是38ms左右
2. 先脸部识别通过后再采样，在有的机器上有会不适配， shader也会警告 ：
WARNING: Calls to any function that may require a gradient calculation inside a conditional block may return undefined results
意思是在if里去做texture2D会有undefined results
3. 采样8个点和采样16个，经测试，没实际性能区别，看来采样并不是耗时瓶颈

###preReadPixel
基本能确定 preRead比 在swapbuffer之后去read性能都是有明显的一定程度的提升

### 发送端的性能
开启美颜，会较明显消耗cpu资源的自个特性：
当发送端的编码速率慢于从上层（生产者）传来的数据速率时候。yuv帧和pcm帧就会在底层积累
经测试发现底层的 *编码*和*发送*两个关键环节（分别占一个线程）里 *编码* 是性能瓶颈
当底层积累未编码数据的时候， 上层能做的事情有：1.降低fps 2.关闭美颜 。 其中关闭美颜的作用是更明显的。
