varying lowp vec2 TexCoordOut;
uniform sampler2D SamplerY;
uniform sampler2D SamplerU;
uniform sampler2D SamplerV;

void main(){
    mediump vec3 yuv;
    lowp vec3 rgb;
    yuv.x = texture2D(SamplerY, TexCoordOut).r;
    yuv.y = texture2D(SamplerU, TexCoordOut).r - 0.5;
    yuv.z = texture2D(SamplerV, TexCoordOut).r - 0.5;
    rgb = mat3(1, 1, 1,0, -0.343, 1.765, 1.4, -0.711, 0.0) * yuv;
    gl_FragColor = vec4(rgb, 1);
}
