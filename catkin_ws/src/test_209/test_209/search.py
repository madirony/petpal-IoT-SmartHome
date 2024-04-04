import rclpy, json, requests

from rclpy.node import Node
from std_msgs.msg import String, Int32
from squaternion import Quaternion
from nav_msgs.msg import Odometry, Path, OccupancyGrid
from geometry_msgs.msg import Twist, PoseStamped, Point32
from ssafy_msgs.msg import TurtlebotStatus
from ros_log_package.RosLogPublisher import RosLogPublisher

params = {
    'homeId' : 2
}

class Search(Node):

    def __init__(self):
        super().__init__('yolo_sub')
        self.odom_sub = self.create_subscription(Odometry,'/odom',self.odom_callback, 10)
        self.goal_pub = self.create_publisher(PoseStamped,'goal_pose', 10)
        self.patrol_sub = self.create_subscription(Int32, '/patrol', self.patrol_callback, 10)
        self.map_sub = self.create_subscription(OccupancyGrid,'/map',self.map_callback,10)
        self.timer = self.create_timer(0.5, self.timer_callback)

        self.goal_msg = PoseStamped()
        self.goal_msg.header.frame_id = 'map'
        self.odom_msg = Odometry()

        self.map_size_x=700
        self.map_size_y=700
        self.map_resolution=0.05

        self.is_odom=False
        self.search = False
        self.is_param = False
        self.is_map = False
        self.is_route = False
        self.is_start = False

        self.idx = 0
        self.route = []

        full_path = 'C:\\Users\\SSAFY\\Desktop\\pppp.txt'
        f=open(full_path,'r')
        lines = f.readlines()
        for line in lines:
            data = line.split()
            self.route.append((int(data[0]), int(data[1])))
        
        self.api_call()
    
    def patrol_callback(self, msg):
        if msg.data == 1:
            if not self.search:
                self.search = True
            else:
                self.idx += 1
                if self.idx >= len(self.route):
                    self.idx = 0

    def odom_callback(self, msg):
        if self.is_param == False and self.is_map == True:
            self.is_param = True

            self.map_offset_x = self.map_msg.info.origin.position.x
            self.map_offset_y = self.map_msg.info.origin.position.y

        self.is_odom = True
        self.odom_msg = msg
    
    def map_callback(self,msg):
        self.is_map = True
        self.map_msg = msg

    def timer_callback(self):
        if not self.search:
            return

        if self.is_param and self.is_map and self.is_odom:

            x=self.odom_msg.pose.pose.position.x
            y=self.odom_msg.pose.pose.position.y
            now_grid_cell=self.pose_to_grid_cell(x,y)

            if not self.is_start:
                min_dis = 10000
                for num, crd in enumerate(self.route):
                    if pow(now_grid_cell[0] - crd[0], 2) + pow(now_grid_cell[1] - crd[1], 2) < min_dis:
                        min_dis = pow(now_grid_cell[0] - crd[0], 2) + pow(now_grid_cell[1] - crd[1], 2)
                        self.idx = num
                
                self.is_start = True

            if abs(now_grid_cell[0] - self.route[self.idx][0]) + abs(now_grid_cell[1] - self.route[self.idx][1]) <= 10:
                self.idx += 1
                if self.idx >= len(self.route):
                    self.idx = 0

            now_goal = self.route[self.idx]
            #print(self.idx, 'th = ', now_goal)
            goal_x, goal_y = self.grid_cell_to_pose(now_goal)
            self.goal_msg.pose.position.x = goal_x
            self.goal_msg.pose.position.y = goal_y

            q = Quaternion.from_euler(0, 0, 0)
            self.goal_msg.pose.orientation.x = q.x
            self.goal_msg.pose.orientation.y = q.y
            self.goal_msg.pose.orientation.z = q.z
            self.goal_msg.pose.orientation.w = q.w

            self.goal_msg.header.stamp = rclpy.clock.Clock().now().to_msg()
            self.goal_pub.publish(self.goal_msg)

    def pose_to_grid_cell(self,x,y):
        map_point_x = int((x - self.map_offset_x) / self.map_resolution)
        map_point_y = int((y - self.map_offset_y) / self.map_resolution)
        
        return map_point_x,map_point_y

    def grid_cell_to_pose(self,grid_cell):
        x = grid_cell[0] * self.map_resolution + self.map_offset_x
        y = grid_cell[1] * self.map_resolution + self.map_offset_y

        return [x,y]

    def api_call(self):
        url = "https://j10a209.p.ssafy.io/api/v1/homes/pet/" + str(params['homeId'])
        
        response = requests.get(url, params=params)
        if response.status_code == 200:
            data = response.json()

            # 변경 필수
            self.goal_x = data['x']
            self.goal_y = data['y']

            self.go_last_point(self.goal_x, self.goal_y)
            
        else:
            self.search = True
    

    def go_last_point(self, x, y):
        self.goal_msg.pose.position.x = x
        self.goal_msg.pose.position.y = y
        q = Quaternion.from_euler(0, 0, 0)

        self.goal_msg.pose.orientation.x = q.x
        self.goal_msg.pose.orientation.y = q.y
        self.goal_msg.pose.orientation.z = q.z
        self.goal_msg.pose.orientation.w = q.w

        self.goal_msg.header.stamp = rclpy.clock.Clock().now().to_msg()
        self.goal_pub.publish(self.goal_msg)


def main(args=None):
    rclpy.init(args=args)
    search = Search()
    rclpy.spin(search)
    search.destroy_node()
    rclpy.shutdown()


if __name__ == '__main__':
    main()