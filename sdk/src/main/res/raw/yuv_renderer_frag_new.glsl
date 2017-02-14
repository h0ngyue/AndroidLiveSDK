varying lowp vec2 TexCoordOut;
uniform sampler2D SamplerY;
uniform sampler2D SamplerU;
uniform sampler2D SamplerV;

void main(){
    mediump vec3 yuv;
    lowp vec3 rgb;

    mediump float curY = texture2D(SamplerY, TexCoordOut).r;
    mediump float curU = texture2D(SamplerU, TexCoordOut).r;
    mediump float curV = texture2D(SamplerV, TexCoordOut).r;
    rgb.x = 1.164 * (curY  - 16.0/255.0) + 1.5958 * (curV - 128.0/255.0);
    rgb.y = 1.164 * (curY - 16.0/255.0) - 0.81290 * (curV - 128.0/255.0) - 0.39173 * (curU - 128.0/255.0);
    rgb.z = 1.164 * (curY - 16.0/255.0) + 2.017 * (curU - 128.0/255.0);
    gl_FragColor = vec4(rgb, 1);
}
